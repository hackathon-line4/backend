package com.sonulim.domain.history.application;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 기록 API의 역할 확인. 역할 확인 공통화는 팀에서 정하기 전까지 도메인 안에만 둔다(link·settings 도메인과 같은 방식).
 */
final class RoleRequirement {

	private RoleRequirement() {
	}

	static void require(AuthenticatedUser user, Role expected) {
		if (user.role() == null) {
			throw new SonulimException(ErrorCode.ROLE_REQUIRED, "역할을 먼저 선택해 주세요");
		}
		if (!expected.name().equals(user.role())) {
			throw new SonulimException(ErrorCode.ROLE_MISMATCH,
					expected == Role.GUARDIAN ? "보호자만 사용할 수 있어요" : "당사자만 사용할 수 있어요");
		}
	}
}
