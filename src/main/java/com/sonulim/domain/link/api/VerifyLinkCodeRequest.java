package com.sonulim.domain.link.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// 형식이 틀린 입력은 400 VALIDATION_FAILED로 끝나고 무작위 대입 시도 횟수에 넣지 않는다.
public record VerifyLinkCodeRequest(
		@NotNull(message = "코드를 입력해 주세요")
		@Pattern(regexp = "\\d{6}", message = "코드는 6자리 숫자예요")
		String code) {
}
