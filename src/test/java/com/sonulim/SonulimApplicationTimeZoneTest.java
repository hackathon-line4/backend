package com.sonulim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// CI 러너는 원래 UTC라 그대로 두면 버그가 재현되지 않는다 — 개발 PC처럼 Asia/Seoul로 바꾼 뒤 검증한다.
class SonulimApplicationTimeZoneTest {

	@BeforeEach
	void setUp() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

	// 테스트 전 값이 아니라 앱이 기대하는 UTC로 되돌린다 — static 블록은 JVM당 한 번만 돌기 때문에,
	// 테스트 전 값(호스트의 KST)으로 되돌리면 같은 JVM에서 이어서 뜨는 스프링 컨텍스트가 KST로 남는다.
	@AfterEach
	void tearDown() {
		SonulimApplication.useUtcAsDefaultTimeZone();
	}

	@Test
	void pinsDefaultTimeZoneToUtcEvenWhenHostIsSeoul() {
		SonulimApplication.useUtcAsDefaultTimeZone();

		assertThat(TimeZone.getDefault().getRawOffset()).isZero();
		assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
	}
}
