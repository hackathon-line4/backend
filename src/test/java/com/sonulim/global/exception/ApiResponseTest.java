package com.sonulim.global.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void successEnvelopeSerializesToOkTrueWithData() throws Exception {
		ApiResponse<String> response = ApiResponse.success("hello");

		String json = objectMapper.writeValueAsString(response);

		assertThat(json).isEqualTo("{\"ok\":true,\"data\":\"hello\"}");
	}

	@Test
	void errorEnvelopeSerializesToOkFalseWithErrorBody() throws Exception {
		ApiResponse<Void> response = ApiResponse.error(ErrorCode.NOT_FOUND, "기록을 찾을 수 없어요", null, null);

		String json = objectMapper.writeValueAsString(response);

		assertThat(json).isEqualTo(
				"{\"ok\":false,\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"기록을 찾을 수 없어요\"}}");
	}

	@Test
	void errorEnvelopeIncludesFieldWhenPresent() throws Exception {
		ApiResponse<Void> response = ApiResponse.error(ErrorCode.VALIDATION_FAILED, "이메일 형식이 아니에요", "email", null);

		String json = objectMapper.writeValueAsString(response);

		assertThat(json).isEqualTo(
				"{\"ok\":false,\"error\":{\"code\":\"VALIDATION_FAILED\",\"message\":\"이메일 형식이 아니에요\",\"field\":\"email\"}}");
	}
}
