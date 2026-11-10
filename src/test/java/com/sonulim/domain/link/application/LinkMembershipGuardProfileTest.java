package com.sonulim.domain.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.stereotype.Component;

import com.sonulim.domain.link.domain.LinkRepository;
import com.sonulim.global.linkaccess.LinkAccessGuard;

/**
 * local/test 전용 통과 구현(DevelopmentLinkAccessGuard)이 없어지고(#75) 실제 구현 하나가 모든 프로파일에서
 * 등록된다 — prod도 기동에 실패하지 않는다.
 */
class LinkMembershipGuardProfileTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withBean(LinkRepository.class, () -> mock(LinkRepository.class))
			.withUserConfiguration(LinkMembershipGuard.class, RequiresLinkAccessGuard.class);

	@ParameterizedTest
	@ValueSource(strings = {"local", "test", "prod", "prod,local", "prod,test"})
	void registersOnlyTheRealGuardOnEveryProfile(String profiles) {
		contextRunner.withPropertyValues("spring.profiles.active=" + profiles)
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(LinkAccessGuard.class);
					assertThat(context.getBean(LinkAccessGuard.class)).isInstanceOf(LinkMembershipGuard.class);
				});
	}

	@Component
	static class RequiresLinkAccessGuard {
		RequiresLinkAccessGuard(LinkAccessGuard guard) {
		}
	}
}
