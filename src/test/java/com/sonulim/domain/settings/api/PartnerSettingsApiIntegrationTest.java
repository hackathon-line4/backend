package com.sonulim.domain.settings.api;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PartnerSettingsApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void guardianSeesRecipientDefaultsBeforeAnyChange() throws Exception {
		String guardian = account("partner-default-g@example.com", "GUARDIAN");
		String recipient = account("partner-default-r@example.com", "RECIPIENT");
		String linkId = link(guardian, recipient);

		partnerSettings(guardian, linkId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.vibration.intensity").value("NORMAL"))
				.andExpect(jsonPath("$.data.vibration.length").value("NORMAL"))
				.andExpect(jsonPath("$.data.vibration.repeatCount").value(2))
				.andExpect(jsonPath("$.data.vibration.updatedAt").value(nullValue()))
				.andExpect(jsonPath("$.data.locationSharing.enabled").value(false))
				.andExpect(jsonPath("$.data.locationSharing.updatedAt").value(nullValue()));
	}

	@Test
	void guardianSeesValuesRecipientChanged() throws Exception {
		String guardian = account("partner-changed-g@example.com", "GUARDIAN");
		String recipient = account("partner-changed-r@example.com", "RECIPIENT");
		String linkId = link(guardian, recipient);
		send(put("/api/v1/me/vibration-settings"), recipient,
				Map.of("intensity", "STRONG", "length", "LONG", "repeatCount", 3)).andExpect(status().isOk());
		send(put("/api/v1/me/location-sharing"), recipient, Map.of("enabled", true)).andExpect(status().isOk());

		partnerSettings(guardian, linkId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.vibration.intensity").value("STRONG"))
				.andExpect(jsonPath("$.data.vibration.length").value("LONG"))
				.andExpect(jsonPath("$.data.vibration.repeatCount").value(3))
				.andExpect(jsonPath("$.data.vibration.updatedAt").isNotEmpty())
				.andExpect(jsonPath("$.data.locationSharing.enabled").value(true))
				.andExpect(jsonPath("$.data.locationSharing.updatedAt").isNotEmpty());
	}

	@Test
	void recipientGets403RoleMismatch() throws Exception {
		String guardian = account("partner-role-g@example.com", "GUARDIAN");
		String recipient = account("partner-role-r@example.com", "RECIPIENT");
		String linkId = link(guardian, recipient);

		partnerSettings(recipient, linkId)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void otherGuardianAndUnknownOrMalformedIdsGet404() throws Exception {
		String guardian = account("partner-404-g@example.com", "GUARDIAN");
		String recipient = account("partner-404-r@example.com", "RECIPIENT");
		String linkId = link(guardian, recipient);
		String otherGuardian = account("partner-404-other@example.com", "GUARDIAN");

		partnerSettings(otherGuardian, linkId)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
		partnerSettings(guardian, UUID.randomUUID().toString())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
		partnerSettings(guardian, "not-a-uuid")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	private ResultActions partnerSettings(String accessToken, String linkId) throws Exception {
		return mockMvc.perform(get("/api/v1/links/" + linkId + "/partner-settings")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String accessToken, Map<String, Object> body)
			throws Exception {
		return mockMvc.perform(request
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)));
	}

	// 연결 코드 → 연결 요청 → 승인까지 거쳐 연결 id를 돌려준다.
	private String link(String guardian, String recipient) throws Exception {
		String code = JsonPath.read(mockMvc.perform(post("/api/v1/link-codes")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardian)
						.header("Idempotency-Key", UUID.randomUUID().toString()))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.data.code");
		String requestId = JsonPath.read(send(post("/api/v1/link-requests")
						.header("Idempotency-Key", UUID.randomUUID().toString()), recipient, Map.of("code", code))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.data.id");
		return JsonPath.read(mockMvc.perform(post("/api/v1/link-requests/" + requestId + "/approve")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardian))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.data.link.id");
	}

	// 가입 후 역할을 고르고, 역할이 담긴 새 액세스 토큰을 돌려준다.
	private String account(String email, String role) throws Exception {
		Map<String, Object> body = Map.of(
				"email", email,
				"password", "abcd1234",
				"name", role.equals("GUARDIAN") ? "이영희" : "김하늘",
				"agreements", Map.of("terms", true, "privacy", true));
		String signupToken = JsonPath.read(mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.data.accessToken");
		return JsonPath.read(send(put("/api/v1/me/role"), signupToken, Map.of("role", role))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.data.accessToken");
	}
}
