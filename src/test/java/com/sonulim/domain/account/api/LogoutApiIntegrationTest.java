package com.sonulim.domain.account.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.sonulim.domain.account.domain.DeviceRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LogoutApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DeviceRepository deviceRepository;

	@Test
	void logoutReturns204AndRefreshTokenCanNoLongerBeUsed() throws Exception {
		String signupBody = signup("logout@example.com");
		String accessToken = JsonPath.read(signupBody, "$.data.accessToken");
		String refreshToken = JsonPath.read(signupBody, "$.data.refreshToken");

		mockMvc.perform(logout(Map.of("refreshToken", refreshToken, "installationId", "inst_abc"))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void logoutRemovesDeviceOfThisInstallation() throws Exception {
		String signupBody = signup("logout-device@example.com");
		String accessToken = JsonPath.read(signupBody, "$.data.accessToken");
		String refreshToken = JsonPath.read(signupBody, "$.data.refreshToken");
		registerDevice(accessToken, "inst_abc");

		mockMvc.perform(logout(Map.of("refreshToken", refreshToken, "installationId", "inst_abc"))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isNoContent());

		assertThat(deviceRepository.findById("inst_abc")).isEmpty();
	}

	@Test
	void logoutKeepsDeviceOfAnotherUser() throws Exception {
		String ownerToken = JsonPath.read(signup("owner@example.com"), "$.data.accessToken");
		registerDevice(ownerToken, "inst_owner");
		String otherBody = signup("other@example.com");

		mockMvc.perform(logout(Map.of("refreshToken", JsonPath.read(otherBody, "$.data.refreshToken"),
						"installationId", "inst_owner"))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(otherBody, "$.data.accessToken")))
				.andExpect(status().isNoContent());

		assertThat(deviceRepository.findById("inst_owner")).isPresent();
	}

	@Test
	void logoutWithoutAccessTokenReturns401() throws Exception {
		mockMvc.perform(logout(Map.of("refreshToken", "token", "installationId", "inst_abc")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void logoutWithoutInstallationIdReturns400() throws Exception {
		String signupBody = signup("noinstall@example.com");
		String accessToken = JsonPath.read(signupBody, "$.data.accessToken");
		String refreshToken = JsonPath.read(signupBody, "$.data.refreshToken");

		mockMvc.perform(logout(Map.of("refreshToken", refreshToken))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("installationId"));
	}

	private void registerDevice(String accessToken, String installationId) throws Exception {
		Map<String, Object> body = Map.of(
				"platform", "ANDROID",
				"pushToken", "push_" + installationId,
				"appVersion", "1.0.0",
				"supportsVibrationIntensity", true);
		mockMvc.perform(put("/api/v1/me/devices/" + installationId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isNoContent());
	}

	private MockHttpServletRequestBuilder logout(Map<String, String> body) throws Exception {
		return post("/api/v1/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body));
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
