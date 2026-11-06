package com.sonulim.domain.vibration.api;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
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
class VibrationSettingsApiIntegrationTest {

	private static final String PATH = "/api/v1/me/vibration-settings";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	// 공통 API라 역할을 고르기 전에도 조회할 수 있다.
	@Test
	void getReturnsDefaultsWithoutUpdatedAtBeforeSaving() throws Exception {
		getSettings(signup("vib-default@example.com"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.intensity").value("NORMAL"))
				.andExpect(jsonPath("$.data.length").value("NORMAL"))
				.andExpect(jsonPath("$.data.repeatCount").value(2))
				.andExpect(jsonPath("$.data.updatedAt").value(nullValue()));
	}

	@Test
	void putSavesSettingsAndGetReturnsThem() throws Exception {
		String accessToken = signup("vib-save@example.com");

		putSettings(accessToken, body("STRONG", "LONG", 3))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.intensity").value("STRONG"))
				.andExpect(jsonPath("$.data.length").value("LONG"))
				.andExpect(jsonPath("$.data.repeatCount").value(3))
				.andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

		getSettings(accessToken)
				.andExpect(jsonPath("$.data.intensity").value("STRONG"))
				.andExpect(jsonPath("$.data.repeatCount").value(3))
				.andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
	}

	@Test
	void putAcceptsNullIntensityForDevicesWithoutIntensityControl() throws Exception {
		putSettings(signup("vib-null@example.com"), body(null, "SHORT", 1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.intensity").value(nullValue()))
				.andExpect(jsonPath("$.data.length").value("SHORT"));
	}

	@Test
	void putWithRepeatCountOutOfRangeReturns400() throws Exception {
		putSettings(signup("vib-repeat@example.com"), body("NORMAL", "NORMAL", 6))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("repeatCount"));
	}

	@Test
	void putWithUnknownLengthReturns400() throws Exception {
		putSettings(signup("vib-length@example.com"), body("NORMAL", "VERY_LONG", 2))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.field").value("length"));
	}

	@Test
	void getWithoutTokenReturns401() throws Exception {
		mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
	}

	private ResultActions getSettings(String accessToken) throws Exception {
		return mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}

	private ResultActions putSettings(String accessToken, Map<String, Object> body) throws Exception {
		return mockMvc.perform(put(PATH)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)));
	}

	private static Map<String, Object> body(String intensity, String length, int repeatCount) {
		Map<String, Object> body = new HashMap<>();
		body.put("intensity", intensity);
		body.put("length", length);
		body.put("repeatCount", repeatCount);
		return body;
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
