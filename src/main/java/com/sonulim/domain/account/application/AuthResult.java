package com.sonulim.domain.account.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.link.domain.Link;

/**
 * 토큰 발급 결과. links·people은 로그인 응답의 links(GET /me와 같은 값)에 쓰고, 그 외에는 비어 있다.
 */
public record AuthResult(String accessToken, String refreshToken, User user, List<Link> links,
		Map<UUID, UserSummary> people) {

	public static AuthResult of(TokenPair tokens, User user) {
		return of(tokens, user, List.of(), Map.of());
	}

	public static AuthResult of(TokenPair tokens, User user, List<Link> links, Map<UUID, UserSummary> people) {
		return new AuthResult(tokens.accessToken(), tokens.refreshToken(), user, links, people);
	}
}
