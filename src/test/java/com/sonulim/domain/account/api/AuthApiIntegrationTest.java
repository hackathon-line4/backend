package com.sonulim.domain.account.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.sonulim.global.security.JwtTokenProvider;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiIntegrationTest {

	private static final String PASSWORD = "abcd1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Test
	void signupReturns201WithTokensAndUserWithoutRole() throws Exception {
		signup("Signup.User@Example.com", PASSWORD, true, true)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ok").value(true))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.data.user.id").isNotEmpty())
				.andExpect(jsonPath("$.data.user.email").value("signup.user@example.com"))
				.andExpect(jsonPath("$.data.user.name").value("이영희"))
				.andExpect(jsonPath("$.data.user.role").value(nullValue()))
				.andExpect(jsonPath("$.data.user.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.data.links", empty()));
	}

	@Test
	void signupAccessTokenBelongsToTheNewUser() throws Exception {
		String body = signup("token@example.com", PASSWORD, true, true)
				.andReturn().getResponse().getContentAsString();
		String accessToken = JsonPath.read(body, "$.data.accessToken");
		String userId = JsonPath.read(body, "$.data.user.id");

		assertThat(jwtTokenProvider.parse(accessToken).userId()).isEqualTo(UUID.fromString(userId));
		assertThat(jwtTokenProvider.parse(accessToken).role()).isNull();
	}

	@Test
	void duplicateEmailIgnoringCaseReturns409() throws Exception {
		signup("dup@example.com", PASSWORD, true, true).andExpect(status().isCreated());

		signup("DUP@example.com", PASSWORD, true, true)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.ok").value(false))
				.andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.error.field").value("email"));
	}

	@Test
	void missingRequiredAgreementReturns400() throws Exception {
		signup("agree@example.com", PASSWORD, true, false)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("AGREEMENT_REQUIRED"))
				.andExpect(jsonPath("$.error.field").value("agreements"));
	}

	@Test
	void passwordWithoutDigitReturns400ValidationFailed() throws Exception {
		signup("weak@example.com", "abcdefgh", true, true)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("password"));
	}

	private ResultActions signup(String email, String password, boolean terms, boolean privacy) throws Exception {
		Map<String, Object> body = Map.of(
				"email", email,
				"password", password,
				"name", "이영희",
				"agreements", Map.of("terms", terms, "privacy", privacy));
		return mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)));
	}
}
