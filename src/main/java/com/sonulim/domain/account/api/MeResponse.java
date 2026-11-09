package com.sonulim.domain.account.api;

import java.util.List;

import com.sonulim.domain.account.domain.User;

/**
 * 앱 시작(스플래시) 때 화면 분기에 쓰는 내 정보. links는 ACTIVE 연결, pendingLinkRequest는 당사자가 보낸 대기 중 연결 요청.
 */
public record MeResponse(UserResponse user, List<Object> links, Object pendingLinkRequest) {

	public static MeResponse from(User user) {
		// links·pendingLinkRequest는 계정 연결(link 도메인)이 구현되기 전까지 빈 값으로 내려준다.
		return new MeResponse(UserResponse.from(user), List.of(), null);
	}
}
