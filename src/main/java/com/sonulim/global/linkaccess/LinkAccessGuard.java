package com.sonulim.global.linkaccess;

import java.util.UUID;

/**
 * 공통 규칙: {@code /links/{linkId}} 하위 API는 그 연결의 구성원만 부를 수 있다. 연결이
 * 없거나 requesterId가 그 연결의 구성원이 아니면, 존재 여부를 숨기기 위해 둘 다 같은
 * 404 NOT_FOUND로 처리한다 — JWT 인증(요청자가 누구인지)과는 별개의 검증이다.
 *
 * 실제 구현은 link 도메인이 제공한다(연결 존재·멤버십 판단). 지금은 link 도메인이 없어서
 * local/test 환경에서만 모두 통과시키는 임시 구현({@link DevelopmentLinkAccessGuard})만 있고,
 * prod 프로파일에는 등록된 구현체가 없어 기동 자체가 실패한다(검증 없이 조용히 통과하는 일이
 * 없도록).
 */
public interface LinkAccessGuard {

	void verifyAccessible(UUID linkId, UUID requesterId);
}
