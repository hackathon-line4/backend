package com.sonulim.domain.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

/**
 * 연결 API 통합 테스트에서 쓰는 가입·역할 선택·코드 만들기 요청.
 */
final class LinkApiTestSupport {

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;

	LinkApiTestSupport(MockMvc mockMvc, ObjectMapper objectMapper) {
		this.mockMvc = mockMvc;
		this.objectMapper = objectMapper;
	}

	/**
	 * 가입하고 역할을 고른 뒤, 역할이 담긴 액세스 토큰을 돌려준다. role이 null이면 역할 선택 없이 가입 토큰을 돌려준다.
	 */
	String signupAs(String email, String name, String role) throws Exception {
		String signupBody = mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"email", email,
								"password", "abcd1234",
								"name", name,
								"agreements", Map.of("terms", true, "privacy", true)))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String accessToken = JsonPath.read(signupBody, "$.data.accessToken");
		if (role == null) {
			return accessToken;
		}
		String roleBody = mockMvc.perform(put("/api/v1/me/role")
						.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("role", role))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(roleBody, "$.data.accessToken");
	}

	ResultActions createCode(String accessToken) throws Exception {
		return createCode(accessToken, UUID.randomUUID().toString());
	}

	ResultActions createCode(String accessToken, String idempotencyKey) throws Exception {
		var request = post("/api/v1/link-codes").header(HttpHeaders.AUTHORIZATION, bearer(accessToken));
		if (idempotencyKey != null) {
			request.header("Idempotency-Key", idempotencyKey);
		}
		return mockMvc.perform(request);
	}

	String createdCode(String accessToken) throws Exception {
		String body = createCode(accessToken)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.code");
	}

	static String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}
}
