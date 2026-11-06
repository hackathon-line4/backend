package com.sonulim.domain.account.api;

import com.sonulim.domain.account.application.AuthResult;

/**
 * 역할 선택 응답. 역할이 담긴 새 액세스·갱신 토큰을 함께 내려준다 — 앱은 저장된 토큰을 이 값으로 바꾼다.
 */
public record RoleSelectionResponse(UserResponse user, String accessToken, String refreshToken) {

	public static RoleSelectionResponse from(AuthResult result) {
		return new RoleSelectionResponse(UserResponse.from(result.user()), result.accessToken(), result.refreshToken());
	}
}
