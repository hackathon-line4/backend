package com.sonulim.global;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UtcToSeoulLocalDateTimeSerializerTest {

	private final ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new SimpleModule().addSerializer(LocalDateTime.class, new UtcToSeoulLocalDateTimeSerializer()));

	@Test
	void convertsStoredUtcTimeToSeoulOffsetMatchingTheCommonRulesExample() throws Exception {
		// 공통 규칙 문서 예시: "2026-11-04T17:55:02+09:00" (UTC 08:55:02 + 9시간)
		LocalDateTime storedUtc = LocalDateTime.of(2026, 11, 4, 8, 55, 2);

		String json = objectMapper.writeValueAsString(storedUtc);

		assertThat(json).isEqualTo("\"2026-11-04T17:55:02+09:00\"");
	}

	@Test
	void dropsSubSecondPrecisionSoOutputIsAlwaysWholeSeconds() throws Exception {
		LocalDateTime withNanos = LocalDateTime.of(2026, 11, 4, 8, 55, 2, 123_456_789);

		String json = objectMapper.writeValueAsString(withNanos);

		assertThat(json).isEqualTo("\"2026-11-04T17:55:02+09:00\"");
	}

	@Test
	void handlesMidnightRolloverAcrossTheDateLine() throws Exception {
		LocalDateTime justBeforeUtcMidnight = LocalDateTime.of(2026, 11, 4, 23, 0, 0);

		String json = objectMapper.writeValueAsString(justBeforeUtcMidnight);

		assertThat(json).isEqualTo("\"2026-11-05T08:00:00+09:00\"");
	}
}
