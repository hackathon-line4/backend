package com.sonulim.domain.link.api;

import static com.sonulim.domain.link.api.LinkApiTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
class LinkCodeApiIntegrationTest {

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
	void createReturnsSixDigitCodeWithQrPayloadValidForTenMinutes() throws Exception {
		String guardian = support.signupAs("create@example.com", "이영희", "GUARDIAN");

		String body = support.createCode(guardian)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ok").value(true))
				.andExpect(jsonPath("$.data.code").value(matchesPattern("\\d{6}")))
				.andReturn().getResponse().getContentAsString();

		String code = JsonPath.read(body, "$.data.code");
		assertThat((String) JsonPath.read(body, "$.data.qrPayload")).isEqualTo("sonullim://link?code=" + code);
		OffsetDateTime createdAt = OffsetDateTime.parse(JsonPath.read(body, "$.data.createdAt"));
		OffsetDateTime expiresAt = OffsetDateTime.parse(JsonPath.read(body, "$.data.expiresAt"));
		assertThat(createdAt.getOffset().getId()).isEqualTo("+09:00");
		assertThat(Duration.between(createdAt, expiresAt)).isEqualTo(Duration.ofMinutes(10));
	}

	@Test
	void creatingNewCodeReplacesCurrentCode() throws Exception {
		String guardian = support.signupAs("replace@example.com", "이영희", "GUARDIAN");
		String first = support.createdCode(guardian);
		generator.willGenerate(first.equals("123456") ? "654321" : "123456");

		String second = support.createdCode(guardian);

		assertThat(second).isNotEqualTo(first);
		getCurrent(guardian)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.code").value(second));
	}

	@Test
	void retryWithSameIdempotencyKeyReturnsSameCode() throws Exception {
		String guardian = support.signupAs("retry@example.com", "이영희", "GUARDIAN");
		String key = UUID.randomUUID().toString();
		String first = JsonPath.read(support.createCode(guardian, key)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.data.code");

		support.createCode(guardian, key)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.code").value(first));
		getCurrent(guardian).andExpect(jsonPath("$.data.code").value(first));
	}

	@Test
	void regeneratesWhenCandidateCollidesWithAnotherLiveCode() throws Exception {
		String otherGuardian = support.signupAs("other@example.com", "김철수", "GUARDIAN");
		generator.willGenerate("111111");
		support.createdCode(otherGuardian);
		String guardian = support.signupAs("collide@example.com", "이영희", "GUARDIAN");
		generator.willGenerate("111111", "222222");

		support.createCode(guardian)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.code").value("222222"));
	}

	@Test
	void reusesCodeValueOnceItHasExpired() throws Exception {
		String otherGuardian = support.signupAs("expired-other@example.com", "김철수", "GUARDIAN");
		generator.willGenerate("333333");
		support.createdCode(otherGuardian);
		clock.advance(Duration.ofMinutes(10));
		String guardian = support.signupAs("reuse@example.com", "이영희", "GUARDIAN");
		generator.willGenerate("333333");

		support.createCode(guardian)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.code").value("333333"));
	}

	@Test
	void returns500WhenNoUnusedCodeAfterTenAttempts() throws Exception {
		String otherGuardian = support.signupAs("full-other@example.com", "김철수", "GUARDIAN");
		generator.willGenerate("444444");
		support.createdCode(otherGuardian);
		String guardian = support.signupAs("full@example.com", "이영희", "GUARDIAN");
		generator.willGenerate("444444", "444444", "444444", "444444", "444444",
				"444444", "444444", "444444", "444444", "444444");

		support.createCode(guardian)
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
	}

	@Test
	void createWithoutIdempotencyKeyReturns400() throws Exception {
		String guardian = support.signupAs("nokey@example.com", "이영희", "GUARDIAN");

		support.createCode(guardian, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
	}

	@Test
	void createWithMalformedIdempotencyKeyReturns400() throws Exception {
		String guardian = support.signupAs("badkey@example.com", "이영희", "GUARDIAN");

		support.createCode(guardian, "not-a-uuid")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("Idempotency-Key"));
	}

	@Test
	void recipientCannotCreateCode() throws Exception {
		String recipient = support.signupAs("recipient-create@example.com", "김민수", "RECIPIENT");

		support.createCode(recipient)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void userWithoutRoleCannotCreateCode() throws Exception {
		String noRole = support.signupAs("norole-create@example.com", "김민수", null);

		support.createCode(noRole)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_REQUIRED"));
	}

	@Test
	void createWithoutTokenReturns401() throws Exception {
		mockMvc.perform(post("/api/v1/link-codes")
						.header("Idempotency-Key", UUID.randomUUID().toString()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void currentReturnsLiveCode() throws Exception {
		String guardian = support.signupAs("current@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);

		getCurrent(guardian)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.code").value(code))
				.andExpect(jsonPath("$.data.qrPayload").value("sonullim://link?code=" + code));
	}

	@Test
	void currentReturns404WhenNoCodeCreated() throws Exception {
		String guardian = support.signupAs("none@example.com", "이영희", "GUARDIAN");

		getCurrent(guardian)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.error.message").value("연결 코드가 없어요"));
	}

	@Test
	void currentReturns404AfterCodeExpires() throws Exception {
		String guardian = support.signupAs("current-expired@example.com", "이영희", "GUARDIAN");
		support.createdCode(guardian);
		clock.advance(Duration.ofMinutes(10));

		getCurrent(guardian)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void recipientCannotGetCurrentCode() throws Exception {
		String recipient = support.signupAs("recipient-current@example.com", "김민수", "RECIPIENT");

		getCurrent(recipient)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	private ResultActions getCurrent(String accessToken) throws Exception {
		return mockMvc.perform(get("/api/v1/link-codes/current").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)));
	}
}
