package com.sonulim.domain.link.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.link.application.LinkCodeChecker.CheckedLinkCode;
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
	private final LinkCodeChecker linkCodeChecker;
	private final ActiveLinkChecker activeLinkChecker;
	private final Clock clock;

	public LinkCodeService(LinkCodeRepository linkCodeRepository, LinkCodeGenerator linkCodeGenerator,
			LinkCodeChecker linkCodeChecker, ActiveLinkChecker activeLinkChecker, Clock clock) {
		this.linkCodeRepository = linkCodeRepository;
		this.linkCodeGenerator = linkCodeGenerator;
		this.linkCodeChecker = linkCodeChecker;
		this.activeLinkChecker = activeLinkChecker;
		this.clock = clock;
	}

	/**
	 * 새 코드를 만들고 이 보호자의 이전 코드를 바로 무효화한다. 같은 Idempotency-Key로 다시 오면 그때 만든 코드를 그대로 돌려준다.
	 * 이미 연결된 보호자는 만들 수 없다(MVP 1:1, ALREADY_LINKED).
	 */
	@Transactional
	public LinkCode create(AuthenticatedUser user, String idempotencyKeyHeader) {
		RoleRequirement.require(user, Role.GUARDIAN);
		UUID idempotencyKey = IdempotencyKeys.parse(idempotencyKeyHeader);

		Optional<LinkCode> alreadyCreated =
				linkCodeRepository.findByGuardianIdAndIdempotencyKey(user.userId(), idempotencyKey);
		if (alreadyCreated.isPresent()) {
			return alreadyCreated.get();
		}
		activeLinkChecker.requireGuardianNotLinked(user.userId());

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
	 * 코드가 맞아도 보호자나 당사자가 이미 연결돼 있으면 ALREADY_LINKED(틀린 시도로 세지 않는다).
	 */
	public LinkCodeVerification verify(AuthenticatedUser user, String code) {
		RoleRequirement.require(user, Role.RECIPIENT);
		CheckedLinkCode checked = linkCodeChecker.check(user.userId(), code);
		User guardian = checked.guardian();
		activeLinkChecker.requireNotLinked(guardian.getId(), user.userId());
		return new LinkCodeVerification(guardian.getId(), guardian.getName(), checked.linkCode().getExpiresAt());
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
}
