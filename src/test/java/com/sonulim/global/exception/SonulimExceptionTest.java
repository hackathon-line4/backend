package com.sonulim.global.exception;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SonulimExceptionTest {

	@Test
	void carriesCodeAndMessage() {
		SonulimException exception = new SonulimException(ErrorCode.NOT_FOUND, "기록을 찾을 수 없어요");

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(exception.getMessage()).isEqualTo("기록을 찾을 수 없어요");
		assertThat(exception.getField()).isNull();
		assertThat(exception.getDetails()).isNull();
	}

	@Test
	void canCarryAFieldNameForInputErrors() {
		SonulimException exception = SonulimException.forField(ErrorCode.VALIDATION_FAILED, "이메일 형식이 아니에요", "email");

		assertThat(exception.getField()).isEqualTo("email");
	}

	@Test
	void canCarryExtraDetails() {
		SonulimException exception = new SonulimException(ErrorCode.TOO_MANY_ATTEMPTS, "잠시 후 다시 시도해 주세요", null,
				Map.of("retryAfterSec", 30));

		assertThat(exception.getDetails()).containsEntry("retryAfterSec", 30);
	}
}
