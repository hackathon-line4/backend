package com.sonulim.domain.link.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.account.domain.UserRepository;
import com.sonulim.domain.link.domain.LinkCode;
import com.sonulim.domain.link.domain.LinkCodeRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.AuthenticatedUser;

@Service
public class LinkCodeService {

	// 살아 있는 코드와 겹치면 다시 뽑는 최대 횟수. 100만 가지 중 살아 있는 코드는 많아야 보호자 수만큼이라 넘길 일이 거의 없다.
	static final int MAX_GENERATION_ATTEMPTS = 10;

	private final LinkCodeRepository linkCodeRepository;
	private final LinkCodeGenerator linkCodeGenerator;
	private final LinkCodeAttemptLimiter attemptLimiter;
	private final UserRepository userRepository;
	private final Clock clock;

	public LinkCodeService(LinkCodeRepository linkCodeRepository, LinkCodeGenerator linkCodeGenerator,
			LinkCodeAttemptLimiter attemptLimiter, UserRepository userRepository, Clock clock) {
		this.linkCodeRepository = linkCodeRepository;
		this.linkCodeGenerator = linkCodeGenerator;
		this.attemptLimiter = attemptLimiter;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	/**
	 * 새 코드를 만들고 이 보호자의 이전 코드를 바로 무효화한다. 같은 Idempotency-Key로 다시 오면 그때 만든 코드를 그대로 돌려준다.
	 */
	@Transactional
	public LinkCode create(AuthenticatedUser user, String idempotencyKeyHeader) {
		RoleRequirement.require(user, Role.GUARDIAN);
		UUID idempotencyKey = parseIdempotencyKey(idempotencyKeyHeader);

		Optional<LinkCode> alreadyCreated =
				linkCodeRepository.findByGuardianIdAndIdempotencyKey(user.userId(), idempotencyKey);
		if (alreadyCreated.isPresent()) {
			return alreadyCreated.get();
		}

		LocalDateTime now = LocalDateTime.now(clock);
		linkCodeRepository.revokeLiveByGuardianId(user.userId(), now);
		return linkCodeRepository.save(LinkCode.issue(user.userId(), nextUnusedCode(now), idempotencyKey, now));
	}

	public LinkCode getCurrent(AuthenticatedUser user) {
		RoleRequirement.require(user, Role.GUARDIAN);
		return linkCodeRepository.findLatestLiveByGuardianId(user.userId(), LocalDateTime.now(clock))
				.orElseThrow(() -> new SonulimException(ErrorCode.NOT_FOUND, "연결 코드가 없어요"));
	}

	/**
	 * 당사자가 입력한 코드를 확인하고 코드를 만든 보호자를 돌려준다. 연결 요청은 만들지 않는다.
	 * 없는 코드·무효화된 코드는 LINK_CODE_NOT_FOUND, 만료된 코드는 LINK_CODE_EXPIRED이고, 둘 다 틀린 시도로 센다.
	 */
	public LinkCodeVerification verify(AuthenticatedUser user, String code) {
		RoleRequirement.require(user, Role.RECIPIENT);
		attemptLimiter.checkNotBlocked(user.userId());

		LocalDateTime now = LocalDateTime.now(clock);
		LinkCode linkCode = linkCodeRepository.findLatestNotRevokedByCode(code)
				.orElseThrow(() -> failedAttempt(user, ErrorCode.LINK_CODE_NOT_FOUND, "코드를 다시 확인해 주세요"));
		if (linkCode.isExpired(now)) {
			throw failedAttempt(user, ErrorCode.LINK_CODE_EXPIRED, "코드가 만료됐어요. 보호자에게 새 코드를 받아 주세요");
		}

		// 코드를 만든 보호자가 없으면(탈퇴 등) 쓸 수 없는 코드로 본다.
		User guardian = userRepository.findById(linkCode.getGuardianId())
				.orElseThrow(() -> failedAttempt(user, ErrorCode.LINK_CODE_NOT_FOUND, "코드를 다시 확인해 주세요"));
		return new LinkCodeVerification(guardian.getId(), guardian.getName(), linkCode.getExpiresAt());
	}

	private SonulimException failedAttempt(AuthenticatedUser user, ErrorCode errorCode, String message) {
		attemptLimiter.recordFailure(user.userId());
		return SonulimException.forField(errorCode, message, "code");
	}

	private String nextUnusedCode(LocalDateTime now) {
		for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
			String candidate = linkCodeGenerator.next();
			if (!linkCodeRepository.existsLiveByCode(candidate, now)) {
				return candidate;
			}
		}
		throw new SonulimException(ErrorCode.INTERNAL_ERROR, "잠시 후 다시 시도해 주세요");
	}

	private static UUID parseIdempotencyKey(String header) {
		if (header == null || header.isBlank()) {
			throw new SonulimException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key 헤더가 필요해요");
		}
		try {
			return UUID.fromString(header.trim());
		} catch (IllegalArgumentException exception) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED, "Idempotency-Key는 UUID 형식이어야 해요",
					"Idempotency-Key");
		}
	}
}
