package com.sonulim.domain.signal.application;

import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

/**
 * 촉신호 관리(signal) API는 전부 보호자 전용이다 — 각 API 명세서 페이지의
 * Authorization 설명("Bearer <token> 형식의 JWT 토큰 · 보호자만") 기준.
 */
final class GuardianOnly {

	private static final String GUARDIAN = "GUARDIAN";

	private GuardianOnly() {
	}

	static void require(String role) {
		if (!GUARDIAN.equals(role)) {
			throw new SonulimException(ErrorCode.ROLE_MISMATCH, "보호자만 사용할 수 있어요");
		}
	}
}
