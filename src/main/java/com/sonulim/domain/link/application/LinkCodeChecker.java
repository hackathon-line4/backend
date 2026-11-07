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
	 * 없는 코드·무효화된 코드는 LINK_CODE_NOT_FOUND, 만료된 코드는 LINK_CODE_EXPIRED이고, 둘 다 틀린 시도로 센다.
	 */
	CheckedLinkCode check(UUID recipientId, String code) {
		attemptLimiter.checkNotBlocked(recipientId);

		LocalDateTime now = LocalDateTime.now(clock);
		LinkCode linkCode = linkCodeRepository.findLatestNotRevokedByCode(code)
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
