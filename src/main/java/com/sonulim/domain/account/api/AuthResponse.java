package com.sonulim.domain.account.api;

import java.util.List;

import com.sonulim.domain.account.application.AuthResult;

/**
 * 회원가입·로그인 공통 응답. 앱은 user.role과 links로 다음 화면(역할 선택 / 계정 연결 / 홈)을 정한다.
 * links는 GET /me의 links와 같은 값이다(API 명세). 가입 직후에는 연결이 없어 항상 빈 배열이다.
 */
public record AuthResponse(String accessToken, String refreshToken, UserResponse user,
		List<MeResponse.LinkItem> links) {

	public static AuthResponse from(AuthResult result) {
		List<MeResponse.LinkItem> links = result.links().stream()
				.map(link -> MeResponse.LinkItem.from(link, result.people()))
				.toList();
		return new AuthResponse(result.accessToken(), result.refreshToken(), UserResponse.from(result.user()), links);
	}
}
