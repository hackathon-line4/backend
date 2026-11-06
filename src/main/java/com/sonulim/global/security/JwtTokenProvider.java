package com.sonulim.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class JwtTokenProvider {

	private static final String ROLE_CLAIM = "role";

	private final SecretKey key;
	private final Duration accessTokenTtl;
	private final Clock clock;

	public JwtTokenProvider(JwtProperties properties, Clock clock) {
		this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
		this.accessTokenTtl = Duration.ofSeconds(properties.accessExpirationSeconds());
		this.clock = clock;
	}

	public String createAccessToken(UUID userId, String role) {
		Instant now = clock.instant();
		JwtBuilder builder = Jwts.builder()
				.subject(userId.toString())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(accessTokenTtl)));
		if (role != null) {
			builder.claim(ROLE_CLAIM, role);
		}
		return builder.signWith(key).compact();
	}

	public AuthenticatedUser parse(String token) {
		try {
			Claims claims = Jwts.parser()
					.verifyWith(key)
					.clock(() -> Date.from(clock.instant()))
					.build()
					.parseSignedClaims(token)
					.getPayload();
			return new AuthenticatedUser(UUID.fromString(claims.getSubject()), claims.get(ROLE_CLAIM, String.class));
		} catch (ExpiredJwtException exception) {
			throw new SonulimException(ErrorCode.TOKEN_EXPIRED, "토큰이 만료되었습니다");
		} catch (JwtException | IllegalArgumentException exception) {
			throw new SonulimException(ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요");
		}
	}
}
