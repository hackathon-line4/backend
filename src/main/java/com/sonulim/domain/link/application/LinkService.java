package com.sonulim.domain.link.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 연결 해제(L-4).
 *
 * 당사자에게 보낼 link.updated 이벤트, 이 연결로 오는 전송을 LINK_ENDED로 막기, 진행 중인 WAITING 신호 타이머 멈추기,
 * 해제 뒤 기록 처리는 아직 없다(docs/domains/link.md 후속 작업·Pending Decisions).
 */
@Service
public class LinkService {

	private final LinkRepository linkRepository;
	private final Clock clock;

	public LinkService(LinkRepository linkRepository, Clock clock) {
		this.linkRepository = linkRepository;
		this.clock = clock;
	}

	/**
	 * 보호자만. 연결을 ENDED로 바꾸고 ended_at을 남긴다(행 삭제 아님). 이미 끝난 연결이면 아무것도 바꾸지 않는다.
	 * 남의 연결은 있는지도 드러내지 않도록 없는 연결과 같은 404로 응답한다.
	 */
	@Transactional
	public void end(AuthenticatedUser user, String linkId) {
		RoleRequirement.require(user, Role.GUARDIAN);
		Link link = linkRepository.findByIdForUpdate(parseId(linkId))
				.filter(found -> found.getGuardianId().equals(user.userId()))
				.orElseThrow(LinkService::notFound);
		link.end(LocalDateTime.now(clock));
	}

	private static UUID parseId(String linkId) {
		try {
			return UUID.fromString(linkId);
		} catch (IllegalArgumentException exception) {
			throw notFound();
		}
	}

	private static SonulimException notFound() {
		return new SonulimException(ErrorCode.NOT_FOUND, "연결을 찾을 수 없어요");
	}
}
