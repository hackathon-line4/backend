package com.sonulim.domain.account.application;

import com.sonulim.domain.account.domain.User;

public record AuthResult(String accessToken, String refreshToken, User user) {
}
