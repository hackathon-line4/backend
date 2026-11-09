package com.sonulim.domain.account.api;

import static org.assertj.core.api.Assertions.assertThat;
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
class TokenRefreshApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Test
	void refreshReturnsNewTokenPairForTheSameUser() throws Exception {
		String signupBody = signup("refresh@example.com");
		String userId = JsonPath.read(signupBody, "$.data.user.id");
		String refreshToken = JsonPath.read(signupBody, "$.data.refreshToken");

		String body = refresh(refreshToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(true))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		String newAccessToken = JsonPath.read(body, "$.data.accessToken");
		String newRefreshToken = JsonPath.read(body, "$.data.refreshToken");
		assertThat(newRefreshToken).isNotEqualTo(refreshToken);
		assertThat(jwtTokenProvider.parse(newAccessToken).userId()).isEqualTo(UUID.fromString(userId));
	}

	@Test
	void reusingUsedRefreshTokenRevokesAllTokensOfTheUser() throws Exception {
		String firstToken = JsonPath.read(signup("reuse@example.com"), "$.data.refreshToken");
		String secondToken = JsonPath.read(
				refresh(firstToken).andReturn().getResponse().getContentAsString(), "$.data.refreshToken");

		refresh(firstToken)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"))
				.andExpect(jsonPath("$.error.message").value("다시 로그인해 주세요"));
		refresh(secondToken)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void unknownRefreshTokenReturns401() throws Exception {
		refresh("not-issued-token")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void blankRefreshTokenReturns400() throws Exception {
		refresh("")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("refreshToken"));
	}

	private String signup(String email) throws Exception {
		Map<String, Object> body = Map.of(
				"email", email,
				"password", "abcd1234",
				"name", "이영희",
				"agreements", Map.of("terms", true, "privacy", true));
		return mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
	}
}
