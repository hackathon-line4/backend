package com.sonulim.domain.link.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sonulim.domain.link.domain.LinkRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

/**
 * MVP 1:1 연결 제약: 이미 살아 있는 연결이 있는 보호자·당사자는 새로 연결할 수 없다(ALREADY_LINKED, Notion API 명세).
 * 승인 시 동시성은 Link의 유니크 컬럼이 마지막으로 막는다.
 */
@Component
class ActiveLinkChecker {

	private final LinkRepository linkRepository;

	ActiveLinkChecker(LinkRepository linkRepository) {
		this.linkRepository = linkRepository;
	}

	void requireGuardianNotLinked(UUID guardianId) {
		if (linkRepository.existsByActiveGuardianId(guardianId)) {
			throw alreadyLinked();
		}
	}

	void requireNotLinked(UUID guardianId, UUID recipientId) {
		requireGuardianNotLinked(guardianId);
		if (linkRepository.existsByActiveRecipientId(recipientId)) {
			throw alreadyLinked();
		}
	}

	static SonulimException alreadyLinked() {
		return new SonulimException(ErrorCode.ALREADY_LINKED, "이미 연결된 계정이 있어요");
	}
}
