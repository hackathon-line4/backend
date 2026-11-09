package com.sonulim.domain.account.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.sonulim.domain.account.domain.Role;

// enum으로 바로 받으면 잘못된 값이 본문 파싱 오류가 되므로, 문자열로 받아 VALIDATION_FAILED(field=role)로 응답한다.
public record RoleRequest(
		@NotNull @Pattern(regexp = "GUARDIAN|RECIPIENT", message = "역할은 GUARDIAN 또는 RECIPIENT 중 하나여야 해요")
		String role) {

	Role toRole() {
		return Role.valueOf(role);
	}
}
