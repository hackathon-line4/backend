package com.sonulim.global.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 예외 → 응답 변환만 검증한다. @WebMvcTest는 SecurityConfig 대신 기본 보안을 켜므로 필터를 끈다(인증은 AuthApiIntegrationTest에서 검증).
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({ GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class })
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void sonulimExceptionMapsToItsOwnHttpStatusAndErrorBody() throws Exception {
		mockMvc.perform(get("/test/sonulim-exception"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.ok").value(false))
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.error.message").value("기록을 찾을 수 없어요"));
	}

	@Test
	void unhandledExceptionMapsTo500InternalError() throws Exception {
		mockMvc.perform(get("/test/unhandled"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.ok").value(false))
				.andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
	}

	@Test
	void validationFailureMapsTo400WithFieldName() throws Exception {
		mockMvc.perform(post("/test/validate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("email"));
	}

	@RestController
	static class TestController {

		@GetMapping("/test/sonulim-exception")
		void throwSonulimException() {
			throw new SonulimException(ErrorCode.NOT_FOUND, "기록을 찾을 수 없어요");
		}

		@GetMapping("/test/unhandled")
		void throwUnhandled() {
			throw new IllegalStateException("boom");
		}

		@PostMapping("/test/validate")
		void validate(@RequestBody @Valid TestRequest request) {
		}

		record TestRequest(@NotBlank(message = "이메일 형식이 아니에요") String email) {
		}
	}
}
