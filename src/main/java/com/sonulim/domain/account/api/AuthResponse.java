package com.sonulim.domain.account.api;

import java.util.List;

import com.sonulim.domain.account.application.AuthResult;

/**
 * 회원가입·로그인 공통 응답. 앱은 user.role과 links로 다음 화면(역할 선택 / 계정 연결 / 홈)을 정한다.
 */
public record AuthResponse(String accessToken, String refreshToken, UserResponse user, List<Object> links) {

	public static AuthResponse from(AuthResult result) {
		// links는 계정 연결(link 도메인)이 구현되기 전까지 빈 배열로 내려준다.
		return new AuthResponse(result.accessToken(), result.refreshToken(), UserResponse.from(result.user()), List.of());
	}
}
