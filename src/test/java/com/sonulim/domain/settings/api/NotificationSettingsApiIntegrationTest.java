package com.sonulim.domain.settings.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationSettingsApiIntegrationTest {

	private static final String PATH = "/api/v1/me/notification-settings";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void guardianGetsDefaultSettings() throws Exception {
		String accessToken = guardian("noti-default@example.com");

		getSettings(accessToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.touchResponseAlert").value(true))
				.andExpect(jsonPath("$.data.noResponseAlert").value(true))
				.andExpect(jsonPath("$.data.responseWaitMinutes").value(10))
				.andExpect(jsonPath("$.data.helpRequestAlert").value(true));
	}

	@Test
	void recipientGets403RoleMismatch() throws Exception {
		String accessToken = selectRole(signup("noti-recipient@example.com"), "RECIPIENT");

		getSettings(accessToken)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void userWithoutRoleGets403RoleRequired() throws Exception {
		getSettings(signup("noti-norole@example.com"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_REQUIRED"));
	}

	@Test
	void patchChangesOnlySentFields() throws Exception {
		String accessToken = guardian("noti-patch@example.com");

		patchSettings(accessToken, Map.of("responseWaitMinutes", 30))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.responseWaitMinutes").value(30))
				.andExpect(jsonPath("$.data.touchResponseAlert").value(true));
		patchSettings(accessToken, Map.of("touchResponseAlert", false)).andExpect(status().isOk());

		getSettings(accessToken)
				.andExpect(jsonPath("$.data.touchResponseAlert").value(false))
				.andExpect(jsonPath("$.data.noResponseAlert").value(true))
				.andExpect(jsonPath("$.data.responseWaitMinutes").value(30));
	}

	@Test
	void patchWithNotAllowedWaitMinutesReturns400() throws Exception {
		patchSettings(guardian("noti-minutes@example.com"), Map.of("responseWaitMinutes", 15))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("responseWaitMinutes"));
	}

	@Test
	void patchWithHelpRequestAlertReturns400FieldReadOnly() throws Exception {
		patchSettings(guardian("noti-help@example.com"), Map.of("helpRequestAlert", false))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("FIELD_READ_ONLY"))
				.andExpect(jsonPath("$.error.message").value("도움 요청 알림은 끌 수 없어요"))
				.andExpect(jsonPath("$.error.field").value("helpRequestAlert"));
	}

	@Test
	void patchByRecipientReturns403() throws Exception {
		String accessToken = selectRole(signup("noti-patch-recipient@example.com"), "RECIPIENT");

		patchSettings(accessToken, Map.of("noResponseAlert", false))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void withoutTokenReturns401() throws Exception {
		mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
	}

	private ResultActions getSettings(String accessToken) throws Exception {
		return mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}

	private ResultActions patchSettings(String accessToken, Map<String, Object> body) throws Exception {
		return mockMvc.perform(patch(PATH)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)));
	}

	private String guardian(String email) throws Exception {
		return selectRole(signup(email), "GUARDIAN");
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

	private String signup(String email) throws Exception {
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
		return JsonPath.read(response, "$.data.accessToken");
	}
}
