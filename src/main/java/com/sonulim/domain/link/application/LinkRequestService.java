package com.sonulim.domain.link.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.account.domain.UserRepository;
import com.sonulim.domain.link.application.LinkCodeChecker.CheckedLinkCode;
import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkCodeRepository;
import com.sonulim.domain.link.domain.LinkRepository;
import com.sonulim.domain.link.domain.LinkRequest;
import com.sonulim.domain.link.domain.LinkRequestRepository;
import com.sonulim.domain.link.domain.LinkRequestStatus;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 연결 요청 흐름: 당사자의 요청 보내기·취소·조회(L-2), 보호자의 받은 요청 목록·승인·거절(L-3).
 *
 * 만료(10분)는 서버 타이머 없이 요청을 읽거나 바꿀 때 판정한다. 푸시(LINK_REQUEST·LINK_RESULT)와 SSE 이벤트
 * (link_request.created·updated)는 실시간 허브·기기 등록이 생기면 붙인다(docs/domains/link.md 후속 작업).
 */
@Service
public class LinkRequestService {

	// 목록에서 status를 생략하면 최근 24시간 전체(Notion API 명세).
	static final Duration DEFAULT_LIST_WINDOW = Duration.ofHours(24);

	private final LinkRequestRepository linkRequestRepository;
	private final LinkRepository linkRepository;
	private final LinkCodeRepository linkCodeRepository;
	private final LinkCodeChecker linkCodeChecker;
	private final ActiveLinkChecker activeLinkChecker;
	private final UserRepository userRepository;
	private final Clock clock;

	public LinkRequestService(LinkRequestRepository linkRequestRepository, LinkRepository linkRepository,
			LinkCodeRepository linkCodeRepository, LinkCodeChecker linkCodeChecker,
			ActiveLinkChecker activeLinkChecker, UserRepository userRepository, Clock clock) {
		this.linkRequestRepository = linkRequestRepository;
		this.linkRepository = linkRepository;
		this.linkCodeRepository = linkCodeRepository;
		this.linkCodeChecker = linkCodeChecker;
		this.activeLinkChecker = activeLinkChecker;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	/**
	 * 코드 검사는 코드 확인 API와 같다. 같은 당사자의 이전 PENDING 요청은 CANCELED로 바꾼다.
	 * 같은 Idempotency-Key로 다시 오면 그때 만든 요청을 그대로 돌려준다.
	 */
	@Transactional
	public LinkRequestDetails create(AuthenticatedUser user, String idempotencyKeyHeader, String code) {
		RoleRequirement.require(user, Role.RECIPIENT);
		UUID idempotencyKey = IdempotencyKeys.parse(idempotencyKeyHeader);
		LocalDateTime now = LocalDateTime.now(clock);

		Optional<LinkRequest> alreadyCreated =
				linkRequestRepository.findByRecipientIdAndIdempotencyKey(user.userId(), idempotencyKey);
		if (alreadyCreated.isPresent()) {
			LinkRequest request = alreadyCreated.get();
			request.expireIfDue(now);
			return details(request);
		}

		CheckedLinkCode checked = linkCodeChecker.check(user.userId(), code);
		activeLinkChecker.requireNotLinked(checked.guardian().getId(), user.userId());

		linkRequestRepository.expireDueByRecipientId(user.userId(), now);
		linkRequestRepository.cancelPendingByRecipientId(user.userId(), now);
		LinkRequest request = linkRequestRepository.save(
				LinkRequest.request(user.userId(), checked.linkCode(), idempotencyKey, now));
		return details(request);
	}

	/**
	 * 요청의 보호자나 당사자만 볼 수 있다. 아니면 404. 만료 시각이 지났으면 이때 EXPIRED로 바꾼다.
	 */
	@Transactional
	public LinkRequestDetails get(AuthenticatedUser user, String requestId) {
		LinkRequest request = linkRequestRepository.findById(parseId(requestId))
				.filter(found -> found.involves(user.userId()))
				.orElseThrow(LinkRequestService::notFound);
		request.expireIfDue(LocalDateTime.now(clock));
		return details(request);
	}

	@Transactional
	public LinkRequestDetails cancel(AuthenticatedUser user, String requestId) {
		RoleRequirement.require(user, Role.RECIPIENT);
		LinkRequest request = lockOwnRequest(requestId, user.userId(), LinkRequest::getRecipientId);
		request.cancel(LocalDateTime.now(clock));
		return details(request);
	}

	/**
	 * 내가 보호자인 요청만 최신순으로. status를 생략하면 최근 24시간 전체.
	 */
	@Transactional
	public List<LinkRequestDetails> list(AuthenticatedUser user, String status) {
		RoleRequirement.require(user, Role.GUARDIAN);
		LinkRequestStatus statusFilter = parseStatus(status);
		LocalDateTime now = LocalDateTime.now(clock);

		linkRequestRepository.expireDueByGuardianId(user.userId(), now);
		List<LinkRequest> requests = statusFilter != null
				? linkRequestRepository.findByGuardianIdAndStatusOrderByRequestedAtDesc(user.userId(), statusFilter)
				: linkRequestRepository.findByGuardianIdAndRequestedAtGreaterThanEqualOrderByRequestedAtDesc(
						user.userId(), now.minus(DEFAULT_LIST_WINDOW));
		return details(requests);
	}

	/**
	 * 한 트랜잭션에서 요청 확인 → 연결 생성 → 요청 APPROVED → 코드 used_at.
	 * 기본 신호 세트 복사·자주 쓰는 신호 채우기는 signal 도메인이 생기면 붙인다(docs/domains/link.md Pending Decisions).
	 */
	@Transactional
	public LinkRequestDetails approve(AuthenticatedUser user, String requestId) {
		RoleRequirement.require(user, Role.GUARDIAN);
		LinkRequest request = lockOwnRequest(requestId, user.userId(), LinkRequest::getGuardianId);
		LocalDateTime now = LocalDateTime.now(clock);
		request.requirePending(now);
		activeLinkChecker.requireNotLinked(request.getGuardianId(), request.getRecipientId());

		Link link;
		try {
			link = linkRepository.saveAndFlush(Link.connect(request.getGuardianId(), request.getRecipientId(), now));
		} catch (DataIntegrityViolationException exception) {
			// 그사이 같은 보호자·당사자의 다른 연결이 먼저 만들어진 경우
			throw ActiveLinkChecker.alreadyLinked();
		}
		request.approve(link.getId(), now);
		linkCodeRepository.findById(request.getLinkCodeId()).ifPresent(linkCode -> linkCode.markUsed(now));
		return new LinkRequestDetails(request, user(request.getGuardianId()), user(request.getRecipientId()), link);
	}

	/**
	 * 코드는 그대로 둔다 — 만료 전이면 다른 당사자가 다시 쓸 수 있다.
	 */
	@Transactional
	public LinkRequestDetails reject(AuthenticatedUser user, String requestId) {
		RoleRequirement.require(user, Role.GUARDIAN);
		LinkRequest request = lockOwnRequest(requestId, user.userId(), LinkRequest::getGuardianId);
		request.reject(LocalDateTime.now(clock));
		return details(request);
	}

	// 남의 요청은 있는지도 드러내지 않도록 없는 요청과 같은 404로 응답한다.
	private LinkRequest lockOwnRequest(String requestId, UUID userId, Function<LinkRequest, UUID> owner) {
		return linkRequestRepository.findByIdForUpdate(parseId(requestId))
				.filter(request -> owner.apply(request).equals(userId))
				.orElseThrow(LinkRequestService::notFound);
	}

	private LinkRequestDetails details(LinkRequest request) {
		Link link = request.getLinkId() == null ? null : linkRepository.findById(request.getLinkId()).orElse(null);
		return new LinkRequestDetails(request, user(request.getGuardianId()), user(request.getRecipientId()), link);
	}

	private List<LinkRequestDetails> details(List<LinkRequest> requests) {
		Set<UUID> userIds = new HashSet<>();
		Set<UUID> linkIds = new HashSet<>();
		for (LinkRequest request : requests) {
			userIds.add(request.getGuardianId());
			userIds.add(request.getRecipientId());
			if (request.getLinkId() != null) {
				linkIds.add(request.getLinkId());
			}
		}
		Map<UUID, User> users = userRepository.findAllById(userIds).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));
		Map<UUID, Link> links = linkRepository.findAllById(linkIds).stream()
				.collect(Collectors.toMap(Link::getId, Function.identity()));
		// 탈퇴 등으로 사용자가 없어진 요청은 목록에서 뺀다.
		return requests.stream()
				.filter(request -> users.containsKey(request.getGuardianId())
						&& users.containsKey(request.getRecipientId()))
				.map(request -> new LinkRequestDetails(request, users.get(request.getGuardianId()),
						users.get(request.getRecipientId()), links.get(request.getLinkId())))
				.toList();
	}

	private User user(UUID userId) {
		return userRepository.findById(userId).orElseThrow(LinkRequestService::notFound);
	}

	private static UUID parseId(String requestId) {
		try {
			return UUID.fromString(requestId);
		} catch (IllegalArgumentException exception) {
			throw notFound();
		}
	}

	private static LinkRequestStatus parseStatus(String status) {
		if (status == null || status.isBlank()) {
			return null;
		}
		try {
			return LinkRequestStatus.valueOf(status.trim());
		} catch (IllegalArgumentException exception) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED,
					"status는 PENDING, APPROVED, REJECTED, CANCELED, EXPIRED 중 하나여야 해요", "status");
		}
	}

	private static SonulimException notFound() {
		return new SonulimException(ErrorCode.NOT_FOUND, "연결 요청을 찾을 수 없어요");
	}
}
