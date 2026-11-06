package com.sonulim.domain.account.api;

import com.sonulim.domain.account.application.TokenPair;

public record TokenResponse(String accessToken, String refreshToken) {

	public static TokenResponse from(TokenPair tokens) {
		return new TokenResponse(tokens.accessToken(), tokens.refreshToken());
	}
}
