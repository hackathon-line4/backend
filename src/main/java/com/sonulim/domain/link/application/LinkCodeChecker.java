package com.sonulim.domain.link.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.account.domain.UserRepository;
import com.sonulim.domain.link.domain.LinkCode;
import com.sonulim.domain.link.domain.LinkCodeRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

/**
 * 당사자가 입력한 코드 검사. 코드 확인(verify)과 연결 요청 보내기가 같은 검사·같은 오류 코드를 쓴다(Notion API 명세).
 * 무작위 대입 시도 횟수도 함께 세서, 요청 API로 시도 제한을 우회하지 못하게 한다.
 */
@Component
class LinkCodeChecker {

	private final LinkCodeRepository linkCodeRepository;
	private final LinkCodeAttemptLimiter attemptLimiter;
	private final UserRepository userRepository;
	private final Clock clock;

	LinkCodeChecker(LinkCodeRepository linkCodeRepository, LinkCodeAttemptLimiter attemptLimiter,
			UserRepository userRepository, Clock clock) {
		this.linkCodeRepository = linkCodeRepository;
		this.attemptLimiter = attemptLimiter;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	/**
	 * 없는 코드·무효화된 코드·사용된 코드는 LINK_CODE_NOT_FOUND, 만료된 코드는 LINK_CODE_EXPIRED이고, 모두 틀린 시도로 센다.
	 *
	 * 사용된 코드(승인으로 연결을 만든 코드)를 막는 것은 명세에 없는 보수적 결정이다 — 연결 해제 뒤 그 코드가 만료 전이면
	 * 다른 당사자가 다시 쓸 수 있게 되기 때문이다(docs/domains/link.md Pending Decisions). 거절된 요청의 코드는 쓰지 않은
	 * 코드라 그대로 쓸 수 있다.
	 */
	CheckedLinkCode check(UUID recipientId, String code) {
		attemptLimiter.checkNotBlocked(recipientId);

		LocalDateTime now = LocalDateTime.now(clock);
		LinkCode linkCode = linkCodeRepository.findLatestNotRevokedByCode(code)
				.filter(found -> !found.isUsed())
				.orElseThrow(() -> failedAttempt(recipientId, ErrorCode.LINK_CODE_NOT_FOUND, "코드를 다시 확인해 주세요"));
		if (linkCode.isExpired(now)) {
			throw failedAttempt(recipientId, ErrorCode.LINK_CODE_EXPIRED, "코드가 만료됐어요. 보호자에게 새 코드를 받아 주세요");
		}

		// 코드를 만든 보호자가 없으면(탈퇴 등) 쓸 수 없는 코드로 본다.
		User guardian = userRepository.findById(linkCode.getGuardianId())
				.orElseThrow(() -> failedAttempt(recipientId, ErrorCode.LINK_CODE_NOT_FOUND, "코드를 다시 확인해 주세요"));
		return new CheckedLinkCode(linkCode, guardian);
	}

	private SonulimException failedAttempt(UUID recipientId, ErrorCode errorCode, String message) {
		attemptLimiter.recordFailure(recipientId);
		return SonulimException.forField(errorCode, message, "code");
	}

	record CheckedLinkCode(LinkCode linkCode, User guardian) {
	}
}
