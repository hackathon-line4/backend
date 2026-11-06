package com.sonulim.domain.account.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.account.domain.UserRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

@Service
public class MeService {

	private final UserRepository userRepository;

	public MeService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public User getMe(UUID userId) {
		return findUser(userId);
	}

	// 토큰은 유효하지만 사용자가 없는 경우(탈퇴 등) — 앱이 로그인 화면으로 가도록 401로 응답한다.
	private User findUser(UUID userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new SonulimException(ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요"));
	}
}
