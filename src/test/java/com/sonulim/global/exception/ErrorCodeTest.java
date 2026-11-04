package com.sonulim.global.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

	@Test
	void hasThirtyThreeCodesFromTheNotionErrorCodeList() {
		assertThat(ErrorCode.values()).hasSize(33);
	}

	@Test
	void mapsEachRepresentativeCodeToItsDocumentedHttpStatus() {
		assertThat(ErrorCode.VALIDATION_FAILED.getHttpStatus()).isEqualTo(400);
		assertThat(ErrorCode.UNAUTHORIZED.getHttpStatus()).isEqualTo(401);
		assertThat(ErrorCode.ROLE_MISMATCH.getHttpStatus()).isEqualTo(403);
		assertThat(ErrorCode.NOT_FOUND.getHttpStatus()).isEqualTo(404);
		assertThat(ErrorCode.LINK_CODE_EXPIRED.getHttpStatus()).isEqualTo(410);
		assertThat(ErrorCode.IDEMPOTENCY_KEY_REUSED.getHttpStatus()).isEqualTo(422);
		assertThat(ErrorCode.TOO_MANY_ATTEMPTS.getHttpStatus()).isEqualTo(429);
		assertThat(ErrorCode.INTERNAL_ERROR.getHttpStatus()).isEqualTo(500);
	}
}
