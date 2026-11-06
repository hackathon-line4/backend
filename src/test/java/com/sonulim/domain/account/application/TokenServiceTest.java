package com.sonulim.domain.account.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sonulim.domain.account.domain.RefreshToken;
import com.sonulim.domain.account.domain.RefreshTokenRepository;
import com.sonulim.domain.account.domain.UserRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.JwtProperties;
import com.sonulim.global.security.JwtTokenProvider;
import com.sonulim.support.MutableClock;

class TokenServiceTest {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-11-06T00:00:00Z"));
	private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
	private final JwtProperties jwtProperties =
			new JwtProperties("test-only-jwt-secret-0123456789abcdef", 3600, 2592000);
	private final TokenService tokenService = new TokenService(refreshTokenRepository, mock(UserRepository.class),
			new JwtTokenProvider(jwtProperties, clock), jwtProperties, clock);

	// 만료는 탈취 신호가 아니다 — 다른 갱신 토큰까지 끊지 않는다.
	@Test
	void expiredRefreshTokenIsRejectedWithoutRevokingOtherTokens() {
		LocalDateTime expiresAt = LocalDateTime.now(clock).plusDays(30);
		given(refreshTokenRepository.findByTokenHash(anyString()))
				.willReturn(Optional.of(RefreshToken.issue(UUID.randomUUID(), "hash", expiresAt)));
		clock.advance(Duration.ofDays(30));

		assertThatThrownBy(() -> tokenService.refresh("expired-token"))
				.isInstanceOf(SonulimException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFRESH_TOKEN);
		verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
	}
}
