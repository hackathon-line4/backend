package com.sonulim.global.linkaccess;

import java.util.UUID;

/**
 * 공통 규칙: {@code /links/{linkId}} 하위 API는 그 연결의 구성원만 부를 수 있다. 연결이
 * 없거나 requesterId가 그 연결의 구성원이 아니면, 존재 여부를 숨기기 위해 둘 다 같은
 * 404 NOT_FOUND로 처리한다 — JWT 인증(요청자가 누구인지)과는 별개의 검증이다.
 *
 * <p>구현은 link 도메인이 제공한다({@code domain.link.application.LinkMembershipGuard}). 다른 도메인은 이
 * 인터페이스만 의존한다. 메서드·{@link LinkMembers}의 이름은 사용하는 도메인(messaging 등)과 맞춰 바꿀 수 있도록
 * 이 패키지에만 모아 둔다(#75).
 */
public interface LinkAccessGuard {

	/**
	 * 조회·관리 API용. 연결이 없거나 구성원이 아니면 404 NOT_FOUND. 해제된(ENDED) 연결도 구성원이면
	 * 통과한다 — 명세의 조회·관리 API 오류 목록에 409가 없다.
	 */
	void verifyAccessible(UUID linkId, UUID requesterId);

	/**
	 * 전송 API용. 연결이 없거나 구성원이 아니면 404 NOT_FOUND, 구성원이지만 해제된(ENDED) 연결이면
	 * 409 LINK_ENDED. 통과하면 이 연결의 보호자·당사자를 돌려준다 — 상대방 id는
	 * {@link LinkMembers#counterpartOf(UUID)}로 얻는다.
	 */
	LinkMembers requireMembers(UUID linkId, UUID requesterId);
}
