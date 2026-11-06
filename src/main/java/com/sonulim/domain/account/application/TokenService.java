package com.sonulim.domain.account.application;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.sonulim.domain.account.domain.RefreshToken;
import com.sonulim.domain.account.domain.RefreshTokenRepository;
import com.sonulim.domain.account.domain.User;
import com.sonulim.global.security.JwtProperties;
import com.sonulim.global.security.JwtTokenProvider;

/**
 * 액세스·갱신 토큰 발급. 회원가입·로그인 등 토큰을 내주는 모든 유스케이스가 이 클래스를 거친다.
 */
@Service
public class TokenService {

	private final RefreshTokenRepository refreshTokenRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final JwtProperties jwtProperties;
	private final Clock clock;

	public TokenService(RefreshTokenRepository refreshTokenRepository, JwtTokenProvider jwtTokenProvider,
			JwtProperties jwtProperties, Clock clock) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.jwtTokenProvider = jwtTokenProvider;
		this.jwtProperties = jwtProperties;
		this.clock = clock;
	}

	public TokenPair issue(User user) {
		String role = user.getRole() == null ? null : user.getRole().name();
		String accessToken = jwtTokenProvider.createAccessToken(user.getId(), role);

		String refreshToken = RefreshTokenCodec.generate();
		LocalDateTime expiresAt = LocalDateTime.now(clock).plusSeconds(jwtProperties.refreshExpirationSeconds());
		refreshTokenRepository.save(RefreshToken.issue(user.getId(), RefreshTokenCodec.hash(refreshToken), expiresAt));

		return new TokenPair(accessToken, refreshToken);
	}
}
