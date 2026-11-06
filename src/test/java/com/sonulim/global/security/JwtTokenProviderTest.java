package com.sonulim.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.support.MutableClock;

import io.jsonwebtoken.security.WeakKeyException;

class JwtTokenProviderTest {

	private static final JwtProperties PROPERTIES =
			new JwtProperties("test-only-jwt-secret-long-enough-for-hs256-0123", 3600, 2592000);

	private final MutableClock clock = new MutableClock(Instant.parse("2026-11-06T00:00:00Z"));
	private final JwtTokenProvider provider = new JwtTokenProvider(PROPERTIES, clock);

	@Test
	void parsesUserIdAndRoleFromIssuedToken() {
		UUID userId = UUID.randomUUID();

		AuthenticatedUser user = provider.parse(provider.createAccessToken(userId, "GUARDIAN"));

		assertThat(user.userId()).isEqualTo(userId);
		assertThat(user.role()).isEqualTo("GUARDIAN");
	}

	@Test
	void roleIsNullBeforeRoleSelection() {
		AuthenticatedUser user = provider.parse(provider.createAccessToken(UUID.randomUUID(), null));

		assertThat(user.role()).isNull();
	}

	@Test
	void rejectsExpiredTokenAsTokenExpired() {
		String token = provider.createAccessToken(UUID.randomUUID(), null);
		clock.advance(Duration.ofSeconds(3601));

		assertThatThrownBy(() -> provider.parse(token))
				.isInstanceOf(SonulimException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOKEN_EXPIRED);
	}

	@Test
	void rejectsTokenSignedWithAnotherKeyAsUnauthorized() {
		JwtTokenProvider other = new JwtTokenProvider(
				new JwtProperties("another-secret-that-is-also-long-enough-0123456", 3600, 2592000), clock);
		String forged = other.createAccessToken(UUID.randomUUID(), "GUARDIAN");

		assertThatThrownBy(() -> provider.parse(forged))
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
	}

	@Test
	void rejectsMalformedTokenAsUnauthorized() {
		assertThatThrownBy(() -> provider.parse("not-a-jwt"))
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
	}

	@Test
	void refusesSecretShorterThan32Bytes() {
		assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties("changeme", 3600, 2592000), clock))
				.isInstanceOf(WeakKeyException.class);
	}
}
