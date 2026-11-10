package com.sonulim.domain.link.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.linkaccess.LinkAccessGuard;
import com.sonulim.global.linkaccess.LinkMembers;

/**
 * LinkAccessGuard의 실제 구현(#75). 모든 프로파일에서 쓴다.
 *
 * <p>구성원인지 먼저 보고 상태는 그다음에 본다 — 남의 해제된 연결도 없는 연결과 같은 404가 되어 존재가 드러나지 않는다.
 * 잠금 없이 읽으므로 해제와 동시에 온 전송 하나는 지나갈 수 있다(docs/domains/link.md Pending Decisions).
 */
@Service
public class LinkMembershipGuard implements LinkAccessGuard {

	private final LinkRepository linkRepository;

	public LinkMembershipGuard(LinkRepository linkRepository) {
		this.linkRepository = linkRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public void verifyAccessible(UUID linkId, UUID requesterId) {
		memberLink(linkId, requesterId);
	}

	@Override
	@Transactional(readOnly = true)
	public LinkMembers requireMembers(UUID linkId, UUID requesterId) {
		Link link = memberLink(linkId, requesterId);
		if (!link.isActive()) {
			throw new SonulimException(ErrorCode.LINK_ENDED, "연결이 해제됐어요");
		}
		return new LinkMembers(link.getId(), link.getGuardianId(), link.getRecipientId());
	}

	private Link memberLink(UUID linkId, UUID requesterId) {
		if (linkId == null || requesterId == null) {
			throw notFound();
		}
		return linkRepository.findById(linkId)
				.filter(link -> link.involves(requesterId))
				.orElseThrow(LinkMembershipGuard::notFound);
	}

	private static SonulimException notFound() {
		return new SonulimException(ErrorCode.NOT_FOUND, "연결을 찾을 수 없어요");
	}
}
