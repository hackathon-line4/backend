package com.sonulim.domain.account.api;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MeLinksApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void recipientSeesPendingRequestBeforeApprovalAndGuardianSeesNone() throws Exception {
		String guardian = account("me-pending-g@example.com", "이영희", "GUARDIAN");
		String recipient = account("me-pending-r@example.com", "김하늘", "RECIPIENT");
		String requestId = requestLink(guardian, recipient);

		me(recipient)
				.andExpect(jsonPath("$.data.links", empty()))
				.andExpect(jsonPath("$.data.pendingLinkRequest.id").value(requestId))
				.andExpect(jsonPath("$.data.pendingLinkRequest.status").value("PENDING"))
				.andExpect(jsonPath("$.data.pendingLinkRequest.guardian.name").value("이영희"))
				.andExpect(jsonPath("$.data.pendingLinkRequest.recipient.name").value("김하늘"))
				.andExpect(jsonPath("$.data.pendingLinkRequest.recipient.role").value("RECIPIENT"))
				.andExpect(jsonPath("$.data.pendingLinkRequest.link").value(nullValue()));
		me(guardian)
				.andExpect(jsonPath("$.data.links", empty()))
				.andExpect(jsonPath("$.data.pendingLinkRequest").value(nullValue()));
	}

	@Test
	void bothSeeActiveLinkAfterApproval() throws Exception {
		String guardian = account("me-active-g@example.com", "이영희", "GUARDIAN");
		String recipient = account("me-active-r@example.com", "김하늘", "RECIPIENT");
		String linkId = approve(guardian, requestLink(guardian, recipient));

		for (String token : new String[] {guardian, recipient}) {
			me(token)
					.andExpect(jsonPath("$.data.links", hasSize(1)))
					.andExpect(jsonPath("$.data.links[0].id").value(linkId))
					.andExpect(jsonPath("$.data.links[0].status").value("ACTIVE"))
					.andExpect(jsonPath("$.data.links[0].guardian.name").value("이영희"))
					.andExpect(jsonPath("$.data.links[0].recipient.name").value("김하늘"))
					.andExpect(jsonPath("$.data.links[0].linkedAt").isNotEmpty())
					.andExpect(jsonPath("$.data.links[0].connection.state").value("UNKNOWN"))
					.andExpect(jsonPath("$.data.pendingLinkRequest").value(nullValue()));
		}
	}

	@Test
	void linksAreEmptyAfterUnlink() throws Exception {
		String guardian = account("me-ended-g@example.com", "이영희", "GUARDIAN");
		String recipient = account("me-ended-r@example.com", "김하늘", "RECIPIENT");
		String linkId = approve(guardian, requestLink(guardian, recipient));

		mockMvc.perform(delete("/api/v1/links/" + linkId).header(HttpHeaders.AUTHORIZATION, "Bearer " + guardian))
				.andExpect(status().isNoContent());

		me(guardian).andExpect(jsonPath("$.data.links", empty()));
		me(recipient).andExpect(jsonPath("$.data.links", empty()));
	}

	private ResultActions me(String accessToken) throws Exception {
		return mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk());
	}

	private String requestLink(String guardian, String recipient) throws Exception {
		String code = JsonPath.read(mockMvc.perform(post("/api/v1/link-codes")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardian)
						.header("Idempotency-Key", UUID.randomUUID().toString()))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.data.code");
		return JsonPath.read(mockMvc.perform(post("/api/v1/link-requests")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + recipient)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("code", code))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.data.id");
	}

	private String approve(String guardian, String requestId) throws Exception {
		return JsonPath.read(mockMvc.perform(post("/api/v1/link-requests/" + requestId + "/approve")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardian))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.data.link.id");
	}

	// 가입 후 역할을 고르고, 역할이 담긴 새 액세스 토큰을 돌려준다.
	private String account(String email, String name, String role) throws Exception {
		Map<String, Object> body = Map.of(
				"email", email,
				"password", "abcd1234",
				"name", name,
				"agreements", Map.of("terms", true, "privacy", true));
		String signupToken = JsonPath.read(mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.data.accessToken");
		return JsonPath.read(mockMvc.perform(put("/api/v1/me/role")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + signupToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("role", role))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.data.accessToken");
	}
}
