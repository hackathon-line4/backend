package com.sonulim.domain.settings.api;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class LocationSharingApiIntegrationTest {

	private static final String PATH = "/api/v1/me/location-sharing";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void recipientGetsDefaultOffWithoutUpdatedAt() throws Exception {
		getSharing(recipient("loc-default@example.com"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.enabled").value(false))
				.andExpect(jsonPath("$.data.updatedAt").value(nullValue()));
	}

	@Test
	void guardianGets403RoleMismatch() throws Exception {
		String accessToken = selectRole(signup("loc-guardian@example.com"), "GUARDIAN");

		getSharing(accessToken)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void userWithoutRoleGets403RoleRequired() throws Exception {
		getSharing(signup("loc-norole@example.com"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_REQUIRED"));
	}

	private ResultActions getSharing(String accessToken) throws Exception {
		return mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}

	private String recipient(String email) throws Exception {
		return selectRole(signup(email), "RECIPIENT");
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
				"name", "김하늘",
				"agreements", Map.of("terms", true, "privacy", true));
		String response = mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.accessToken");
	}
}
