package com.sonulim.domain.account.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.sonulim.domain.account.application.SignupCommand;

/**
 * 비밀번호 규칙(8자 이상, 영문·숫자 포함)은 공통 규칙 문서 기준. '비밀번호 확인' 일치 검사는 앱에서만 한다.
 */
public record SignupRequest(
		@NotBlank(message = "이메일을 입력해 주세요")
		@Email(message = "이메일 형식이 아니에요")
		String email,

		@NotBlank(message = "비밀번호를 입력해 주세요")
		@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "비밀번호는 8자 이상, 영문과 숫자를 포함해야 해요")
		String password,

		@NotBlank(message = "이름을 입력해 주세요")
		@Size(max = 20, message = "이름은 20자 이내로 입력해 주세요")
		String name,

		@NotNull(message = "필수 약관에 동의해 주세요")
		@Valid
		Agreements agreements) {

	public record Agreements(boolean terms, boolean privacy) {
	}

	public SignupCommand toCommand() {
		return new SignupCommand(email, password, name, agreements.terms(), agreements.privacy());
	}
}
