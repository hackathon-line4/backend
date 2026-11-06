package com.sonulim.domain.account.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.domain.RefreshToken;
import com.sonulim.domain.account.domain.RefreshTokenRepository;
import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.account.domain.UserRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.JwtProperties;
import com.sonulim.global.security.JwtTokenProvider;

/**
 * 액세스·갱신 토큰 발급과 갱신 토큰 회전. 회원가입·로그인 등 토큰을 내주는 모든 유스케이스가 이 클래스를 거친다.
 */
@Service
public class TokenService {

	private final RefreshTokenRepository refreshTokenRepository;
	private final UserRepository userRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final JwtProperties jwtProperties;
	private final Clock clock;

	public TokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository,
			JwtTokenProvider jwtTokenProvider, JwtProperties jwtProperties, Clock clock) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.userRepository = userRepository;
		this.jwtTokenProvider = jwtTokenProvider;
		this.jwtProperties = jwtProperties;
		this.clock = clock;
	}

	public TokenPair issue(User user) {
		String refreshToken = RefreshTokenCodec.generate();
		saveRefreshToken(user.getId(), refreshToken);
		return new TokenPair(createAccessToken(user), refreshToken);
	}

	/**
	 * 쓴 갱신 토큰은 바로 무효화하고 새 토큰을 발급한다. 이미 무효화된 토큰이 다시 오면 탈취로 보고
	 * 그 사용자의 갱신 토큰을 모두 무효화한다 — 이 무효화는 오류 응답과 함께 커밋되어야 해서 롤백하지 않는다.
	 */
	@Transactional(noRollbackFor = SonulimException.class)
	public TokenPair refresh(String rawRefreshToken) {
		LocalDateTime now = LocalDateTime.now(clock);
		RefreshToken current = refreshTokenRepository.findByTokenHash(RefreshTokenCodec.hash(rawRefreshToken))
				.orElseThrow(TokenService::invalidRefreshToken);
		if (current.isRevoked()) {
			refreshTokenRepository.revokeAllByUserId(current.getUserId(), now);
			throw invalidRefreshToken();
		}
		if (current.isExpired(now)) {
			throw invalidRefreshToken();
		}
		User user = userRepository.findById(current.getUserId())
				.orElseThrow(TokenService::invalidRefreshToken);

		String refreshToken = RefreshTokenCodec.generate();
		RefreshToken next = saveRefreshToken(user.getId(), refreshToken);
		current.rotateTo(next.getId(), now);
		return new TokenPair(createAccessToken(user), refreshToken);
	}

	private String createAccessToken(User user) {
		String role = user.getRole() == null ? null : user.getRole().name();
		return jwtTokenProvider.createAccessToken(user.getId(), role);
	}

	private RefreshToken saveRefreshToken(UUID userId, String refreshToken) {
		LocalDateTime expiresAt = LocalDateTime.now(clock).plusSeconds(jwtProperties.refreshExpirationSeconds());
		return refreshTokenRepository.save(RefreshToken.issue(userId, RefreshTokenCodec.hash(refreshToken), expiresAt));
	}

	private static SonulimException invalidRefreshToken() {
		return new SonulimException(ErrorCode.INVALID_REFRESH_TOKEN, "다시 로그인해 주세요");
	}
}
