package com.sonulim.domain.link.api;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.sonulim.domain.link.application.LinkCodeGenerator;
import com.sonulim.support.MutableClock;

/**
 * 연결 코드 통합 테스트용: 시간을 앞으로 보낼 수 있는 Clock과, 정해 둔 코드를 먼저 내주는 생성기.
 */
@TestConfiguration
class LinkCodeTestConfig {

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock(Instant.now());
	}

	@Bean
	@Primary
	ScriptedLinkCodeGenerator scriptedLinkCodeGenerator() {
		return new ScriptedLinkCodeGenerator();
	}

	/**
	 * 넣어 둔 코드가 있으면 그것부터 내주고, 다 쓰면 실제 난수로 만든다.
	 */
	static class ScriptedLinkCodeGenerator extends LinkCodeGenerator {

		private final Deque<String> scripted = new ArrayDeque<>();

		void willGenerate(String... codes) {
			scripted.addAll(List.of(codes));
		}

		void reset() {
			scripted.clear();
		}

		@Override
		public String next() {
			String code = scripted.poll();
			return code != null ? code : super.next();
		}
	}
}
