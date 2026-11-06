package com.sonulim.global;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시각 계산(토큰 만료, 로그인 잠금 등)은 Clock을 주입받아 한다 — 테스트에서 시간을 고정·이동하기 위해서다.
 * 서버 시각은 UTC(공통 규칙).
 */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
