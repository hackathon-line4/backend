package com.sonulim.global.linkaccess;

import java.util.UUID;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 실제 연결 없이 임의의 linkId로 API를 부르는 테스트용 — 연결 접근 검증을 모두 통과시킨다.
 * {@code @Import(PassThroughLinkAccessGuardConfig.class)}로 필요한 테스트 클래스에만 붙인다.
 * 연결 접근 검증 자체를 확인하는 테스트에는 붙이지 않는다.
 */
@TestConfiguration
public class PassThroughLinkAccessGuardConfig {

	@Bean
	@Primary
	LinkAccessGuard passThroughLinkAccessGuard() {
		return new LinkAccessGuard() {

			@Override
			public void verifyAccessible(UUID linkId, UUID requesterId) {
				// 통과시킨다 — 테스트 전용.
			}

			@Override
			public LinkMembers requireMembers(UUID linkId, UUID requesterId) {
				// 실제 연결이 없으니 구성원을 알 수 없다. 전송 API 테스트는 실제 연결을 만들어 쓴다.
				throw new UnsupportedOperationException("통과용 설정은 연결 구성원을 알 수 없어요");
			}
		};
	}
}
