package com.sonulim.domain.account.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.ResultActions;
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

	@Test
	void selectRoleReturnsUserAndNewTokensCarryingTheRole() throws Exception {
		String accessToken = JsonPath.read(signup("guardian@example.com"), "$.data.accessToken");

		String body = selectRole(accessToken, "GUARDIAN")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.role").value("GUARDIAN"))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		String newAccessToken = JsonPath.read(body, "$.data.accessToken");
		assertThat(jwtTokenProvider.parse(newAccessToken).role()).isEqualTo("GUARDIAN");
		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken))
				.andExpect(jsonPath("$.data.user.role").value("GUARDIAN"));
	}

	@Test
	void selectingSameRoleAgainReturns200() throws Exception {
		String accessToken = JsonPath.read(signup("again@example.com"), "$.data.accessToken");
		selectRole(accessToken, "RECIPIENT").andExpect(status().isOk());

		selectRole(accessToken, "RECIPIENT")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.user.role").value("RECIPIENT"));
	}

	@Test
	void selectingDifferentRoleReturns409() throws Exception {
		String accessToken = JsonPath.read(signup("change@example.com"), "$.data.accessToken");
		selectRole(accessToken, "GUARDIAN").andExpect(status().isOk());

		selectRole(accessToken, "RECIPIENT")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ROLE_ALREADY_SET"))
				.andExpect(jsonPath("$.error.message").value("이미 역할을 선택했어요"));
	}

	@Test
	void unknownRoleReturns400() throws Exception {
		String accessToken = JsonPath.read(signup("badrole@example.com"), "$.data.accessToken");

		selectRole(accessToken, "ADMIN")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("role"));
	}

	private ResultActions selectRole(String accessToken, String role) throws Exception {
		return mockMvc.perform(put("/api/v1/me/role")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("role", role))));
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
