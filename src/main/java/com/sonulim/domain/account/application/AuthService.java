package com.sonulim.domain.account.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.domain.AgreementType;
import com.sonulim.domain.account.domain.RefreshToken;
import com.sonulim.domain.account.domain.RefreshTokenRepository;
import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.account.domain.UserAgreement;
import com.sonulim.domain.account.domain.UserAgreementRepository;
import com.sonulim.domain.account.domain.UserRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.JwtProperties;
import com.sonulim.global.security.JwtTokenProvider;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final UserAgreementRepository userAgreementRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final JwtProperties jwtProperties;
	private final LoginAttemptLimiter loginAttemptLimiter;
	private final Clock clock;

	public AuthService(UserRepository userRepository, UserAgreementRepository userAgreementRepository,
			RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
			JwtTokenProvider jwtTokenProvider, JwtProperties jwtProperties, LoginAttemptLimiter loginAttemptLimiter,
			Clock clock) {
		this.userRepository = userRepository;
		this.userAgreementRepository = userAgreementRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
		this.jwtProperties = jwtProperties;
		this.loginAttemptLimiter = loginAttemptLimiter;
		this.clock = clock;
	}

	@Transactional
	public AuthResult signup(SignupCommand command) {
		if (!command.termsAgreed() || !command.privacyAgreed()) {
			throw SonulimException.forField(ErrorCode.AGREEMENT_REQUIRED, "필수 약관에 동의해 주세요", "agreements");
		}
		String email = User.normalizeEmail(command.email());
		if (userRepository.existsByEmail(email)) {
			throw emailAlreadyExists();
		}

		User user;
		try {
			user = userRepository.saveAndFlush(
					User.register(email, passwordEncoder.encode(command.password()), command.name()));
		} catch (DataIntegrityViolationException exception) {
			// 같은 이메일 가입이 동시에 들어와 존재 확인을 함께 통과한 경우
			throw emailAlreadyExists();
		}

		LocalDateTime now = LocalDateTime.now(clock);
		userAgreementRepository.saveAll(List.of(
				UserAgreement.agree(user.getId(), AgreementType.TERMS, now),
				UserAgreement.agree(user.getId(), AgreementType.PRIVACY, now)));
		return issueTokens(user);
	}

	/**
	 * 없는 이메일과 틀린 비밀번호를 같은 오류로 응답한다 — 계정 존재 여부를 드러내지 않기 위해서다.
	 */
	@Transactional
	public AuthResult login(String rawEmail, String password) {
		String email = User.normalizeEmail(rawEmail);
		loginAttemptLimiter.checkNotLocked(email);

		User user = userRepository.findByEmail(email)
				.filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
				.orElse(null);
		if (user == null) {
			loginAttemptLimiter.recordFailure(email);
			throw new SonulimException(ErrorCode.INVALID_CREDENTIALS, "이메일 또는 비밀번호가 맞지 않아요");
		}

		loginAttemptLimiter.recordSuccess(email);
		return issueTokens(user);
	}

	private AuthResult issueTokens(User user) {
		String role = user.getRole() == null ? null : user.getRole().name();
		String accessToken = jwtTokenProvider.createAccessToken(user.getId(), role);

		String refreshToken = RefreshTokenCodec.generate();
		LocalDateTime expiresAt = LocalDateTime.now(clock).plusSeconds(jwtProperties.refreshExpirationSeconds());
		refreshTokenRepository.save(RefreshToken.issue(user.getId(), RefreshTokenCodec.hash(refreshToken), expiresAt));

		return new AuthResult(accessToken, refreshToken, user);
	}

	private static SonulimException emailAlreadyExists() {
		return SonulimException.forField(ErrorCode.EMAIL_ALREADY_EXISTS, "이미 가입된 이메일이에요", "email");
	}
}
