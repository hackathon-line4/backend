package com.sonulim.domain.account.api;

import java.time.LocalDateTime;
import java.util.UUID;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.account.domain.User;

public record UserResponse(UUID id, String email, String name, Role role, LocalDateTime createdAt) {

	static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole(), user.getCreatedAt());
	}
}
