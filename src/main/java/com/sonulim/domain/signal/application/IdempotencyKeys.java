package com.sonulim.domain.signal.application;

import java.util.UUID;

import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

/**
 * Idempotency-Key 헤더 읽기. 공통 처리가 아직 없어서(link 도메인과 동일하게) signal API
 * 안에서만 쓴다.
 */
final class IdempotencyKeys {

	private IdempotencyKeys() {
	}

	static UUID parse(String header) {
		if (header == null || header.isBlank()) {
			throw new SonulimException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key 헤더가 필요해요");
		}
		try {
			return UUID.fromString(header.trim());
		} catch (IllegalArgumentException exception) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED, "Idempotency-Key는 UUID 형식이어야 해요",
					"Idempotency-Key");
		}
	}
}
