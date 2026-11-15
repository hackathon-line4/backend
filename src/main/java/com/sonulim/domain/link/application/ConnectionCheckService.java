package com.sonulim.domain.link.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.link.domain.ConnectionCheck;
import com.sonulim.domain.link.domain.ConnectionCheck.AckOutcome;
import com.sonulim.domain.link.domain.ConnectionCheckRepository;
import com.sonulim.domain.link.domain.ConnectionCheckStatus;
import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.push.PushMessage;
import com.sonulim.global.push.PushSender;
import com.sonulim.global.push.PushType;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 연결 확인(T-2): 요청·ack·결과 조회. 결과는 Link의 connection.state·lastConfirmedAt에 반영한다.
 *
 * <ul>
 *   <li>타임아웃은 서버 타이머 없이 확인을 읽거나 바꿀 때 판정한다. 그래서 아무도 확인을 다시 읽지 않으면 연결 상태가
 *       UNCONFIRMED로 늦게 바뀔 수 있다(docs/domains/link.md 후속 작업).</li>
 *   <li>새 확인을 만들면 요청자의 상대방에게 조용한 푸시(CONNECTION_CHECK)와 connection_check.requested를,
 *       시간 안에 ack가 와서 확인되면 보호자·당사자 둘 다에게 connection_check.updated를 보낸다. 둘 다 커밋 뒤에 나간다.
 *       늦은 ack와 시간 초과는 이벤트를 보내지 않는다(docs/domains/link.md Pending Decisions).</li>
 *   <li>연결의 구성원이 아니거나, 해제된 연결이거나, 없는·형식이 틀린 id는 모두 404 NOT_FOUND(Pending Decisions).</li>
 *   <li>잠그는 순서는 항상 연결 → 확인이다 — 요청·ack·조회가 동시에 와도 엇갈려 기다리지 않는다.</li>
 * </ul>
 */
@Service
public class ConnectionCheckService {

	private final ConnectionCheckRepository connectionCheckRepository;
	private final LinkRepository linkRepository;
	private final PushSender pushSender;
	private final ConnectionCheckEvents connectionCheckEvents;
	private final Clock clock;

	public ConnectionCheckService(ConnectionCheckRepository connectionCheckRepository, LinkRepository linkRepository,
			PushSender pushSender, ConnectionCheckEvents connectionCheckEvents, Clock clock) {
		this.connectionCheckRepository = connectionCheckRepository;
		this.linkRepository = linkRepository;
		this.pushSender = pushSender;
		this.connectionCheckEvents = connectionCheckEvents;
		this.clock = clock;
	}

	/**
	 * 같은 연결에 아직 끝나지 않은 확인이 있으면 새로 만들지 않고 그것을 돌려준다(누가 요청했든).
	 * 상대 기기는 새 확인을 만들 때만 깨운다 — 버튼을 여러 번 눌러도 여러 번 깨지 않는다.
	 */
	@Transactional
	public ConnectionCheck request(AuthenticatedUser user, String linkId) {
		Link link = lockActiveLink(parseId(linkId), found -> found.involves(user.userId()));
		LocalDateTime now = LocalDateTime.now(clock);

		Optional<ConnectionCheck> pending = connectionCheckRepository
				.findFirstByLinkIdAndStatusOrderByRequestedAtDesc(link.getId(), ConnectionCheckStatus.PENDING);
		if (pending.isPresent()) {
			ConnectionCheck check = pending.get();
			if (!check.resolveIfDue(now)) {
				return check;
			}
			link.connectionUnconfirmed();
		}
		ConnectionCheck check = connectionCheckRepository.save(ConnectionCheck.request(link.getId(), user.userId(), now));
		UUID counterpartId = counterpartOf(link, user.userId());
		pushSender.send(PushMessage.to(counterpartId, PushType.CONNECTION_CHECK)
				.with("checkId", check.getId().toString())
				.build());
		connectionCheckEvents.requested(check, counterpartId);
		return check;
	}

	/**
	 * 요청한 사람이 아닌 쪽만(본인이 보내면 404, 명세 해석). 여러 번 와도 처음 한 번만 기록한다.
	 * 타임아웃 뒤에 와도 Link의 lastConfirmedAt은 갱신한다(명세).
	 */
	@Transactional
	public void ack(AuthenticatedUser user, String checkId) {
		UUID id = parseId(checkId);
		ConnectionCheck unlocked = connectionCheckRepository.findById(id).orElseThrow(ConnectionCheckService::notFound);
		Link link = lockActiveLink(unlocked.getLinkId(), found -> found.involves(user.userId()));
		ConnectionCheck check = connectionCheckRepository.findByIdForUpdate(id)
				.filter(found -> !found.getRequestedBy().equals(user.userId()))
				.orElseThrow(ConnectionCheckService::notFound);

		LocalDateTime now = LocalDateTime.now(clock);
		if (check.resolveIfDue(now)) {
			link.connectionUnconfirmed();
		}
		AckOutcome outcome = check.ack(now);
		if (outcome == AckOutcome.CONFIRMED) {
			link.connectionConfirmed(now);
			connectionCheckEvents.updated(check, link);
		} else if (outcome == AckOutcome.LATE) {
			link.connectionSeenAt(now);
		}
	}

	/**
	 * timeoutAt이 지났는데 아직 PENDING이면 조회 시점에 UNCONFIRMED로 바꿔 돌려준다(명세).
	 */
	@Transactional
	public ConnectionCheck get(AuthenticatedUser user, String checkId) {
		UUID id = parseId(checkId);
		ConnectionCheck unlocked = connectionCheckRepository.findById(id).orElseThrow(ConnectionCheckService::notFound);
		Link link = lockActiveLink(unlocked.getLinkId(), found -> found.involves(user.userId()));
		ConnectionCheck check = connectionCheckRepository.findByIdForUpdate(id)
				.orElseThrow(ConnectionCheckService::notFound);

		if (check.resolveIfDue(LocalDateTime.now(clock))) {
			link.connectionUnconfirmed();
		}
		return check;
	}

	private Link lockActiveLink(UUID linkId, Predicate<Link> accessible) {
		return linkRepository.findByIdForUpdate(linkId)
				.filter(Link::isActive)
				.filter(accessible)
				.orElseThrow(ConnectionCheckService::notFound);
	}

	private static UUID counterpartOf(Link link, UUID userId) {
		return link.getGuardianId().equals(userId) ? link.getRecipientId() : link.getGuardianId();
	}

	private static UUID parseId(String id) {
		try {
			return UUID.fromString(id);
		} catch (IllegalArgumentException exception) {
			throw notFound();
		}
	}

	private static SonulimException notFound() {
		return new SonulimException(ErrorCode.NOT_FOUND, "연결 확인을 찾을 수 없어요");
	}
}
