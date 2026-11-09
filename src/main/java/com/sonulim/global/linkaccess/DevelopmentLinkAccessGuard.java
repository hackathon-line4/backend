package com.sonulim.global.linkaccess;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * link 도메인이 아직 없어서 연결 존재·멤버십을 실제로 확인할 방법이 없다 — local/test
 * 환경에서만 모든 linkId를 통과시키는 임시 구현이다. link 도메인이 실제 구현을 내놓으면
 * 이 클래스는 지운다.
 *
 * prod에서는 절대 활성화되지 않는다 — local/test와 prod가 같이 켜진 상태에서도 마찬가지다.
 * (단순히 local/test만 나열하면 prod와 동시에 켜졌을 때도 이 임시 구현이 등록될 수 있어서,
 * "!prod"로 명시적으로 막는다.) 그래서 prod 배포에서는 이 빈이 없어 LinkAccessGuard를
 * 필요로 하는 서비스가 뜨지 못하고 기동 자체가 실패한다 — link 도메인의 실제 구현으로
 * 반드시 교체해야 한다.
 */
@Component
@Profile("(local | test) & !prod")
public class DevelopmentLinkAccessGuard implements LinkAccessGuard {

	@Override
	public void verifyAccessible(UUID linkId, UUID requesterId) {
		// 통과시킨다 — 개발 전용.
	}
}
