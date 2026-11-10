package com.sonulim.global.linkaccess;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DevelopmentLinkAccessGuard는 local/test 전용 통과 구현이다. prod가 활성화되면(단독이든
 * local·test와 같이 켜지든) 절대 등록되면 안 되고, 그 결과로 LinkAccessGuard를 필수로 요구하는
 * 쪽은 "첫 요청"이 아니라 "앱 기동 시점"에 실패해야 한다 — 실제 Guard 없이 조용히 통과하는
 * 일이 없도록.
 */
class LinkAccessGuardProfileTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(DevelopmentLinkAccessGuard.class, RequiresLinkAccessGuard.class);

	@Test
	void registersThePassThroughGuardOnLocalProfile() {
		contextRunner.withPropertyValues("spring.profiles.active=local")
				.run(context -> assertThat(context).hasSingleBean(LinkAccessGuard.class));
	}

	@Test
	void registersThePassThroughGuardOnTestProfile() {
		contextRunner.withPropertyValues("spring.profiles.active=test")
				.run(context -> assertThat(context).hasSingleBean(LinkAccessGuard.class));
	}

	@Test
	void failsAtStartupOnProdProfileAlone() {
		contextRunner.withPropertyValues("spring.profiles.active=prod")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class);
					assertThat(context.getStartupFailure().getCause()).hasMessageContaining("LinkAccessGuard");
				});
	}

	@Test
	void failsAtStartupWhenProdIsCombinedWithLocal() {
		contextRunner.withPropertyValues("spring.profiles.active=prod,local")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class);
				});
	}

	@Test
	void failsAtStartupWhenProdIsCombinedWithTest() {
		contextRunner.withPropertyValues("spring.profiles.active=prod,test")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class);
				});
	}

	@Component
	static class RequiresLinkAccessGuard {
		RequiresLinkAccessGuard(LinkAccessGuard guard) {
		}
	}
}
