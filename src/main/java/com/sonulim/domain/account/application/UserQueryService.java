package com.sonulim.domain.account.application;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.sonulim.domain.account.domain.UserRepository;

/**
 * 다른 도메인(messaging·link 등)이 사용자 이름·역할을 읽는 창구. UserRepository를 직접 쓰지 않고 이 서비스를 거친다.
 *
 * <p>MeService는 로그인한 본인 조회라 사용자가 없으면 401을 던지지만, 여기서는 없는 사용자를 빈 값으로 돌려준다 —
 * 없을 때 어떻게 응답할지(404, 이름 숨김 등)는 호출하는 쪽이 정한다.
 */
@Service
public class UserQueryService {

	private final UserRepository userRepository;

	public UserQueryService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public Optional<UserSummary> find(UUID userId) {
		return userRepository.findById(userId).map(UserSummary::from);
	}

	/**
	 * 목록 화면처럼 여러 명이 필요할 때 한 번의 조회로 가져온다. 없는 ID는 결과 Map에 들어가지 않는다.
	 */
	public Map<UUID, UserSummary> findAll(Collection<UUID> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		return userRepository.findAllById(userIds).stream()
				.map(UserSummary::from)
				.collect(Collectors.toUnmodifiableMap(UserSummary::id, Function.identity()));
	}
}
