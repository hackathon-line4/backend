package com.sonulim.domain.account.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
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
import com.sonulim.domain.account.domain.Device;
import com.sonulim.domain.account.domain.DeviceRepository;
import com.sonulim.domain.account.domain.Platform;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeviceApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DeviceRepository deviceRepository;

	@Test
	void registerDeviceReturns204AndStoresDevice() throws Exception {
		Session user = signup("device@example.com");

		register(user.accessToken(), "inst_a", deviceBody("ANDROID", "push_a")).andExpect(status().isNoContent());

		Device device = deviceRepository.findById("inst_a").orElseThrow();
		assertThat(device.getUserId()).isEqualTo(user.userId());
		assertThat(device.getPlatform()).isEqualTo(Platform.ANDROID);
		assertThat(device.getPushToken()).isEqualTo("push_a");
		assertThat(device.getAppVersion()).isEqualTo("1.0.0");
		assertThat(device.isSupportsVibrationIntensity()).isTrue();
	}

	@Test
	void registeringSameInstallationAgainUpdatesPushToken() throws Exception {
		Session user = signup("update@example.com");
		register(user.accessToken(), "inst_a", deviceBody("IOS", "push_old")).andExpect(status().isNoContent());

		register(user.accessToken(), "inst_a", deviceBody("IOS", "push_new")).andExpect(status().isNoContent());

		assertThat(deviceRepository.findById("inst_a").orElseThrow().getPushToken()).isEqualTo("push_new");
	}

	@Test
	void registeringNewInstallationRemovesPreviousDeviceOfUser() throws Exception {
		Session user = signup("newphone@example.com");
		register(user.accessToken(), "inst_old", deviceBody("ANDROID", "push_old")).andExpect(status().isNoContent());

		register(user.accessToken(), "inst_new", deviceBody("ANDROID", "push_new")).andExpect(status().isNoContent());

		assertThat(deviceRepository.findById("inst_old")).isEmpty();
		assertThat(deviceRepository.findById("inst_new")).isPresent();
	}

	@Test
	void samePushTokenOnAnotherUsersInstallationIsRemoved() throws Exception {
		Session previousOwner = signup("previous@example.com");
		Session newOwner = signup("new-owner@example.com");
		register(previousOwner.accessToken(), "inst_prev", deviceBody("ANDROID", "push_shared"))
				.andExpect(status().isNoContent());

		register(newOwner.accessToken(), "inst_next", deviceBody("ANDROID", "push_shared"))
				.andExpect(status().isNoContent());

		assertThat(deviceRepository.findById("inst_prev")).isEmpty();
		assertThat(deviceRepository.findById("inst_next").orElseThrow().getUserId()).isEqualTo(newOwner.userId());
	}

	@Test
	void loggingInWithAnotherAccountOnSameInstallationChangesOwner() throws Exception {
		Session first = signup("first@example.com");
		Session second = signup("second@example.com");
		register(first.accessToken(), "inst_a", deviceBody("IOS", "push_a")).andExpect(status().isNoContent());

		register(second.accessToken(), "inst_a", deviceBody("IOS", "push_a")).andExpect(status().isNoContent());

		assertThat(deviceRepository.findById("inst_a").orElseThrow().getUserId()).isEqualTo(second.userId());
	}

	@Test
	void getMeByRecipientUpdatesLastSeenAt() throws Exception {
		Session user = signup("recipient@example.com");
		String accessToken = selectRole(user.accessToken(), "RECIPIENT");
		register(accessToken, "inst_a", deviceBody("ANDROID", "push_a")).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk());

		assertThat(deviceRepository.findById("inst_a").orElseThrow().getLastSeenAt()).isNotNull();
	}

	@Test
	void getMeByGuardianDoesNotUpdateLastSeenAt() throws Exception {
		Session user = signup("guardian-seen@example.com");
		String accessToken = selectRole(user.accessToken(), "GUARDIAN");
		register(accessToken, "inst_a", deviceBody("IOS", "push_a")).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk());

		assertThat(deviceRepository.findById("inst_a").orElseThrow().getLastSeenAt()).isNull();
	}

	@Test
	void unknownPlatformReturns400() throws Exception {
		Session user = signup("platform@example.com");

		register(user.accessToken(), "inst_a", deviceBody("WINDOWS", "push_a"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("platform"));
	}

	@Test
	void missingVibrationSupportFlagReturns400() throws Exception {
		Session user = signup("flag@example.com");
		Map<String, Object> body = deviceBody("IOS", "push_a");
		body.remove("supportsVibrationIntensity");

		register(user.accessToken(), "inst_a", body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.field").value("supportsVibrationIntensity"));
	}

	@Test
	void tooLongInstallationIdReturns400() throws Exception {
		Session user = signup("longid@example.com");

		register(user.accessToken(), "i".repeat(65), deviceBody("IOS", "push_a"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.field").value("installationId"));
	}

	@Test
	void registerDeviceWithoutTokenReturns401() throws Exception {
		mockMvc.perform(put("/api/v1/me/devices/inst_a")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(deviceBody("IOS", "push_a"))))
				.andExpect(status().isUnauthorized());
	}

	private ResultActions register(String accessToken, String installationId, Map<String, Object> body)
			throws Exception {
		return mockMvc.perform(put("/api/v1/me/devices/" + installationId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)));
	}

	private String selectRole(String accessToken, String role) throws Exception {
		String response = mockMvc.perform(put("/api/v1/me/role")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("role", role))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.accessToken");
	}

	private static Map<String, Object> deviceBody(String platform, String pushToken) {
		Map<String, Object> body = new HashMap<>();
		body.put("platform", platform);
		body.put("pushToken", pushToken);
		body.put("appVersion", "1.0.0");
		body.put("supportsVibrationIntensity", true);
		return body;
	}

	private Session signup(String email) throws Exception {
		Map<String, Object> body = Map.of(
				"email", email,
				"password", "abcd1234",
				"name", "이영희",
				"agreements", Map.of("terms", true, "privacy", true));
		String response = mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return new Session(UUID.fromString(JsonPath.read(response, "$.data.user.id")),
				JsonPath.read(response, "$.data.accessToken"));
	}

	private record Session(UUID userId, String accessToken) {
	}
}
