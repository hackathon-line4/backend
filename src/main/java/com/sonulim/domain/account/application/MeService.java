package com.sonulim.domain.account.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.account.domain.UserRepository;
import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkRequest;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

@Service
public class MeService {

	private final UserRepository userRepository;
	private final TokenService tokenService;
	private final DeviceService deviceService;
	private final MyLinksReader myLinksReader;
	private final UserQueryService userQueryService;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	MeService(UserRepository userRepository, TokenService tokenService, DeviceService deviceService,
			MyLinksReader myLinksReader, UserQueryService userQueryService, ApplicationEventPublisher eventPublisher,
			Clock clock) {
		this.userRepository = userRepository;
		this.tokenService = tokenService;
		this.deviceService = deviceService;
		this.myLinksReader = myLinksReader;
		this.userQueryService = userQueryService;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public MeDetails getMe(UUID userId) {
		User user = findUser(userId);
		// 당사자가 앱을 마지막으로 연 시각을 기기에 남긴다(API 명세: 당사자 호출 시 last_seen_at 갱신).
		if (user.getRole() == Role.RECIPIENT) {
			deviceService.markSeen(userId);
		}

		List<Link> links = myLinksReader.activeLinks(userId);
		// 보호자는 연결 요청을 보내지 않으므로 항상 null이다(API 명세).
		LinkRequest pending = user.getRole() == Role.RECIPIENT
				? myLinksReader.pendingRequestOfRecipient(userId).orElse(null)
				: null;

		Set<UUID> peopleIds = new HashSet<>();
		links.forEach(link -> {
			peopleIds.add(link.getGuardianId());
			peopleIds.add(link.getRecipientId());
		});
		if (pending != null) {
			peopleIds.add(pending.getGuardianId());
			peopleIds.add(pending.getRecipientId());
		}
		return new MeDetails(user, links, pending, userQueryService.findAll(peopleIds));
	}

	/**
	 * 액세스 토큰에 역할이 담기므로 역할을 저장한 뒤 새 토큰을 발급해 돌려준다 — 앱은 다시 로그인하지 않고 역할 API를 쓸 수 있다.
	 */
	@Transactional
	public AuthResult selectRole(UUID userId, Role role) {
		User user = findUser(userId);
		user.selectRole(role, LocalDateTime.now(clock));
		eventPublisher.publishEvent(new RoleSelectedEvent(user.getId(), user.getRole()));
		return AuthResult.of(tokenService.issue(user), user);
	}

	// 토큰은 유효하지만 사용자가 없는 경우(탈퇴 등) — 앱이 로그인 화면으로 가도록 401로 응답한다.
	private User findUser(UUID userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new SonulimException(ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요"));
	}
}
