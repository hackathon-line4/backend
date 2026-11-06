package com.sonulim.domain.account.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.account.domain.UserRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

@Service
public class MeService {

	private final UserRepository userRepository;
	private final TokenService tokenService;
	private final Clock clock;

	public MeService(UserRepository userRepository, TokenService tokenService, Clock clock) {
		this.userRepository = userRepository;
		this.tokenService = tokenService;
		this.clock = clock;
	}

	public User getMe(UUID userId) {
		return findUser(userId);
	}

	/**
	 * 액세스 토큰에 역할이 담기므로 역할을 저장한 뒤 새 토큰을 발급해 돌려준다 — 앱은 다시 로그인하지 않고 역할 API를 쓸 수 있다.
	 */
	@Transactional
	public AuthResult selectRole(UUID userId, Role role) {
		User user = findUser(userId);
		user.selectRole(role, LocalDateTime.now(clock));
		return AuthResult.of(tokenService.issue(user), user);
	}

	// 토큰은 유효하지만 사용자가 없는 경우(탈퇴 등) — 앱이 로그인 화면으로 가도록 401로 응답한다.
	private User findUser(UUID userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new SonulimException(ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요"));
	}
}
