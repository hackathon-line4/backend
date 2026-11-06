package com.sonulim.domain.account.api;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.sonulim.global.security.JwtTokenProvider;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MeApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Test
	void getMeReturnsUserWithEmptyLinksAndNoPendingRequest() throws Exception {
		String signupBody = signup("me@example.com");
		String userId = JsonPath.read(signupBody, "$.data.user.id");
		String accessToken = JsonPath.read(signupBody, "$.data.accessToken");

		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.id").value(userId))
				.andExpect(jsonPath("$.data.user.email").value("me@example.com"))
				.andExpect(jsonPath("$.data.user.name").value("이영희"))
				.andExpect(jsonPath("$.data.user.role").value(nullValue()))
				.andExpect(jsonPath("$.data.links", empty()))
				.andExpect(jsonPath("$.data", hasKey("pendingLinkRequest")))
				.andExpect(jsonPath("$.data.pendingLinkRequest").value(nullValue()));
	}

	@Test
	void getMeWithoutTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/v1/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void getMeForUnknownUserReturns401() throws Exception {
		String accessToken = jwtTokenProvider.createAccessToken(UUID.randomUUID(), null);

		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
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
}
