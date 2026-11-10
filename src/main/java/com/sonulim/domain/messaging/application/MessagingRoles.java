package com.sonulim.domain.messaging.application;

import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 역할 검사. 역할을 아직 안 골랐으면 ROLE_REQUIRED, 다른 역할이면 ROLE_MISMATCH(공통 규칙).
 *
 * <p>AuthenticatedUser가 역할을 문자열로 들고 있어 여기서도 문자열로 비교한다 — global이 account에 의존하지 않기 위해서다.
 */
final class MessagingRoles {

	private static final String GUARDIAN = "GUARDIAN";
	private static final String RECIPIENT = "RECIPIENT";

	private MessagingRoles() {
	}

	static void requireGuardian(AuthenticatedUser user) {
		require(user, GUARDIAN, "보호자만 사용할 수 있어요");
	}

	static void requireRecipient(AuthenticatedUser user) {
		require(user, RECIPIENT, "당사자만 사용할 수 있어요");
	}

	private static void require(AuthenticatedUser user, String role, String message) {
		if (user.role() == null) {
			throw new SonulimException(ErrorCode.ROLE_REQUIRED, "역할을 먼저 선택해 주세요");
		}
		if (!role.equals(user.role())) {
			throw new SonulimException(ErrorCode.ROLE_MISMATCH, message);
		}
	}
}
