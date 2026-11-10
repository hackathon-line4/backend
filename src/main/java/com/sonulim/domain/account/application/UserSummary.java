package com.sonulim.domain.account.application;

import java.util.UUID;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.account.domain.User;

/**
 * 다른 도메인에 내주는 사용자 정보. 화면에 이름·역할을 보여 주는 데 필요한 만큼만 담는다(이메일 등은 제외).
 */
public record UserSummary(UUID id, String name, Role role) {

	static UserSummary from(User user) {
		return new UserSummary(user.getId(), user.getName(), user.getRole());
	}
}
