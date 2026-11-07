package com.sonulim.domain.link.api;

import static com.sonulim.domain.link.api.LinkApiTestSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.sonulim.domain.link.api.LinkCodeTestConfig.ScriptedLinkCodeGenerator;
import com.sonulim.support.MutableClock;

@SpringBootTest
@AutoConfigureMockMvc
@Import(LinkCodeTestConfig.class)
@Transactional
class LinkCodeVerifyApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private MutableClock clock;

	@Autowired
	private ScriptedLinkCodeGenerator generator;

	private LinkApiTestSupport support;

	@BeforeEach
	void setUp() {
		support = new LinkApiTestSupport(mockMvc, objectMapper);
		generator.reset();
	}

	@Test
	void verifyReturnsGuardianWhoCreatedTheCode() throws Exception {
		String guardian = support.signupAs("verify-guardian@example.com", "이영희", "GUARDIAN");
		String guardianId = JsonPath.read(mockMvc.perform(get("/api/v1/me")
						.header(HttpHeaders.AUTHORIZATION, bearer(guardian)))
				.andReturn().getResponse().getContentAsString(), "$.data.user.id");
		String created = support.createCode(guardian).andReturn().getResponse().getContentAsString();
		String code = JsonPath.read(created, "$.data.code");
		String expiresAt = JsonPath.read(created, "$.data.expiresAt");
		String recipient = support.signupAs("verify-recipient@example.com", "김민수", "RECIPIENT");

		verify(recipient, code)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(true))
				.andExpect(jsonPath("$.data.guardian.id").value(guardianId))
				.andExpect(jsonPath("$.data.guardian.name").value("이영희"))
				.andExpect(jsonPath("$.data.expiresAt").value(expiresAt));
	}

	@Test
	void verifyDoesNotConsumeTheCode() throws Exception {
		String guardian = support.signupAs("keep-guardian@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String recipient = support.signupAs("keep-recipient@example.com", "김민수", "RECIPIENT");

		verify(recipient, code).andExpect(status().isOk());

		verify(recipient, code).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/link-codes/current").header(HttpHeaders.AUTHORIZATION, bearer(guardian)))
				.andExpect(jsonPath("$.data.code").value(code));
	}

	@Test
	void unknownCodeReturns404OnCodeField() throws Exception {
		String recipient = support.signupAs("unknown@example.com", "김민수", "RECIPIENT");

		verify(recipient, "000000")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.ok").value(false))
				.andExpect(jsonPath("$.error.code").value("LINK_CODE_NOT_FOUND"))
				.andExpect(jsonPath("$.error.message").value("코드를 다시 확인해 주세요"))
				.andExpect(jsonPath("$.error.field").value("code"));
	}

	@Test
	void revokedCodeReturns404() throws Exception {
		String guardian = support.signupAs("revoked-guardian@example.com", "이영희", "GUARDIAN");
		generator.willGenerate("555555", "666666");
		support.createdCode(guardian);
		support.createdCode(guardian);
		String recipient = support.signupAs("revoked-recipient@example.com", "김민수", "RECIPIENT");

		verify(recipient, "555555")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("LINK_CODE_NOT_FOUND"));
		verify(recipient, "666666").andExpect(status().isOk());
	}

	@Test
	void expiredCodeReturns410OnCodeField() throws Exception {
		String guardian = support.signupAs("expired-guardian@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String recipient = support.signupAs("expired-recipient@example.com", "김민수", "RECIPIENT");
		clock.advance(Duration.ofMinutes(10));

		verify(recipient, code)
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.error.code").value("LINK_CODE_EXPIRED"))
				.andExpect(jsonPath("$.error.message").value("코드가 만료됐어요. 보호자에게 새 코드를 받아 주세요"))
				.andExpect(jsonPath("$.error.field").value("code"));
	}

	@Test
	void malformedCodeReturns400() throws Exception {
		String recipient = support.signupAs("malformed@example.com", "김민수", "RECIPIENT");

		verify(recipient, "12a456")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("code"));
	}

	@Test
	void blocksAfterTenWrongAttempts() throws Exception {
		String recipient = support.signupAs("brute@example.com", "김민수", "RECIPIENT");
		for (int i = 0; i < 10; i++) {
			verify(recipient, "000000").andExpect(status().isNotFound());
		}

		verify(recipient, "000000")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error.code").value("TOO_MANY_ATTEMPTS"))
				.andExpect(jsonPath("$.error.message").value("잠시 후 다시 시도해 주세요"))
				.andExpect(jsonPath("$.error.details.retryAfterSec").isNumber());
	}

	@Test
	void malformedCodesDoNotCountAsAttempts() throws Exception {
		String guardian = support.signupAs("format-guardian@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String recipient = support.signupAs("format@example.com", "김민수", "RECIPIENT");
		for (int i = 0; i < 10; i++) {
			verify(recipient, "abc").andExpect(status().isBadRequest());
		}

		verify(recipient, code).andExpect(status().isOk());
	}

	@Test
	void guardianCannotVerify() throws Exception {
		String guardian = support.signupAs("guardian-verify@example.com", "이영희", "GUARDIAN");

		verify(guardian, "123456")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void userWithoutRoleCannotVerify() throws Exception {
		String noRole = support.signupAs("norole-verify@example.com", "김민수", null);

		verify(noRole, "123456")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_REQUIRED"));
	}

	@Test
	void verifyWithoutTokenReturns401() throws Exception {
		mockMvc.perform(post("/api/v1/link-codes/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("code", "123456"))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	private ResultActions verify(String accessToken, String code) throws Exception {
		return mockMvc.perform(post("/api/v1/link-codes/verify")
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("code", code))));
	}
}
