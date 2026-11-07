package com.sonulim.domain.link.api;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.sonulim.domain.link.api.LinkCodeTestConfig.ScriptedLinkCodeGenerator;
import com.sonulim.support.MutableClock;

/**
 * 당사자 쪽 연결 요청: 보내기·조회·취소(L-2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(LinkCodeTestConfig.class)
@Transactional
class LinkRequestApiIntegrationTest {

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
	void sendingRequestReturnsPendingRequestWithBothPeople() throws Exception {
		String guardian = support.signupAs("lrq-send-g@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String recipient = support.signupAs("lrq-send-r@example.com", "김하늘", "RECIPIENT");

		support.sendRequest(recipient, code)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ok").value(true))
				.andExpect(jsonPath("$.data.id").isNotEmpty())
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.guardian.id").value(support.userId(guardian)))
				.andExpect(jsonPath("$.data.guardian.name").value("이영희"))
				.andExpect(jsonPath("$.data.recipient.id").value(support.userId(recipient)))
				.andExpect(jsonPath("$.data.recipient.name").value("김하늘"))
				.andExpect(jsonPath("$.data.recipient.role").value("RECIPIENT"))
				.andExpect(jsonPath("$.data.code").value(code))
				.andExpect(jsonPath("$.data.requestedAt").isNotEmpty())
				.andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
				.andExpect(jsonPath("$.data.decidedAt").value(nullValue()))
				.andExpect(jsonPath("$.data.link").value(nullValue()));
	}

	@Test
	void sendingWithoutIdempotencyKeyReturns400() throws Exception {
		String code = support.createdCode(support.signupAs("lrq-nokey-g@example.com", "이영희", "GUARDIAN"));
		String recipient = support.signupAs("lrq-nokey-r@example.com", "김하늘", "RECIPIENT");

		support.sendRequest(recipient, code, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
	}

	@Test
	void sameIdempotencyKeyReturnsTheSameRequest() throws Exception {
		String code = support.createdCode(support.signupAs("lrq-idem-g@example.com", "이영희", "GUARDIAN"));
		String recipient = support.signupAs("lrq-idem-r@example.com", "김하늘", "RECIPIENT");
		String key = UUID.randomUUID().toString();

		String first = JsonPath.read(support.sendRequest(recipient, code, key)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.data.id");

		support.sendRequest(recipient, code, key)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.id").value(first))
				.andExpect(jsonPath("$.data.status").value("PENDING"));
	}

	@Test
	void unknownCodeReturns404LikeVerify() throws Exception {
		String recipient = support.signupAs("lrq-unknown-r@example.com", "김하늘", "RECIPIENT");
		generator.willGenerate("111111");
		support.createdCode(support.signupAs("lrq-unknown-g@example.com", "이영희", "GUARDIAN"));

		support.sendRequest(recipient, "999999")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("LINK_CODE_NOT_FOUND"))
				.andExpect(jsonPath("$.error.field").value("code"));
	}

	@Test
	void expiredCodeReturns410LikeVerify() throws Exception {
		String code = support.createdCode(support.signupAs("lrq-expcode-g@example.com", "이영희", "GUARDIAN"));
		String recipient = support.signupAs("lrq-expcode-r@example.com", "김하늘", "RECIPIENT");
		clock.advance(Duration.ofMinutes(10));

		support.sendRequest(recipient, code)
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.error.code").value("LINK_CODE_EXPIRED"))
				.andExpect(jsonPath("$.error.message").value("코드가 만료됐어요. 보호자에게 새 코드를 받아 주세요"))
				.andExpect(jsonPath("$.error.field").value("code"));
	}

	@Test
	void guardianCannotSendRequest() throws Exception {
		String guardian = support.signupAs("lrq-role-g@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);

		support.sendRequest(guardian, code)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void newRequestCancelsPreviousPendingRequestOfSameRecipient() throws Exception {
		String firstCode = support.createdCode(support.signupAs("lrq-prev-g1@example.com", "이영희", "GUARDIAN"));
		String secondCode = support.createdCode(support.signupAs("lrq-prev-g2@example.com", "박지성", "GUARDIAN"));
		String recipient = support.signupAs("lrq-prev-r@example.com", "김하늘", "RECIPIENT");
		String firstRequest = support.sentRequestId(recipient, firstCode);

		support.sendRequest(recipient, secondCode).andExpect(status().isCreated());

		support.getRequest(recipient, firstRequest)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("CANCELED"))
				.andExpect(jsonPath("$.data.decidedAt").isNotEmpty());
	}

	@Test
	void guardianAndRecipientCanBothGetTheRequest() throws Exception {
		String guardian = support.signupAs("lrq-get-g@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String recipient = support.signupAs("lrq-get-r@example.com", "김하늘", "RECIPIENT");
		String requestId = support.sentRequestId(recipient, code);

		support.getRequest(guardian, requestId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(requestId))
				.andExpect(jsonPath("$.data.status").value("PENDING"));
		support.getRequest(recipient, requestId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(requestId));
	}

	@Test
	void otherUserGets404() throws Exception {
		String code = support.createdCode(support.signupAs("lrq-other-g@example.com", "이영희", "GUARDIAN"));
		String recipient = support.signupAs("lrq-other-r@example.com", "김하늘", "RECIPIENT");
		String requestId = support.sentRequestId(recipient, code);
		String stranger = support.signupAs("lrq-other-s@example.com", "최민지", "RECIPIENT");

		support.getRequest(stranger, requestId)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void malformedRequestIdReturns404() throws Exception {
		String recipient = support.signupAs("lrq-badid-r@example.com", "김하늘", "RECIPIENT");

		support.getRequest(recipient, "lrq_7")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	// 서버 타이머가 아직 없으므로 조회 시점에 만료를 판정한다(Notion API 명세).
	@Test
	void requestReadsAsExpiredTenMinutesAfterSending() throws Exception {
		String code = support.createdCode(support.signupAs("lrq-exp-g@example.com", "이영희", "GUARDIAN"));
		String recipient = support.signupAs("lrq-exp-r@example.com", "김하늘", "RECIPIENT");
		String requestId = support.sentRequestId(recipient, code);
		clock.advance(Duration.ofMinutes(10));

		support.getRequest(recipient, requestId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("EXPIRED"))
				.andExpect(jsonPath("$.data.decidedAt").value(nullValue()));
	}

	@Test
	void cancelingPendingRequestReturnsCanceled() throws Exception {
		String code = support.createdCode(support.signupAs("lrq-cancel-g@example.com", "이영희", "GUARDIAN"));
		String recipient = support.signupAs("lrq-cancel-r@example.com", "김하늘", "RECIPIENT");
		String requestId = support.sentRequestId(recipient, code);

		support.decide(recipient, requestId, "cancel")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("CANCELED"))
				.andExpect(jsonPath("$.data.decidedAt").isNotEmpty())
				.andExpect(jsonPath("$.data.link").value(nullValue()));
	}

	@Test
	void cancelingTwiceReturns409WithCurrentStatus() throws Exception {
		String code = support.createdCode(support.signupAs("lrq-twice-g@example.com", "이영희", "GUARDIAN"));
		String recipient = support.signupAs("lrq-twice-r@example.com", "김하늘", "RECIPIENT");
		String requestId = support.sentRequestId(recipient, code);
		support.decide(recipient, requestId, "cancel").andExpect(status().isOk());

		support.decide(recipient, requestId, "cancel")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("LINK_REQUEST_NOT_PENDING"))
				.andExpect(jsonPath("$.error.message").value("이미 처리된 요청이에요"))
				.andExpect(jsonPath("$.error.details.status").value("CANCELED"));
	}

	@Test
	void cancelingExpiredRequestReturns409Expired() throws Exception {
		String code = support.createdCode(support.signupAs("lrq-cexp-g@example.com", "이영희", "GUARDIAN"));
		String recipient = support.signupAs("lrq-cexp-r@example.com", "김하늘", "RECIPIENT");
		String requestId = support.sentRequestId(recipient, code);
		clock.advance(Duration.ofMinutes(10));

		support.decide(recipient, requestId, "cancel")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("LINK_REQUEST_NOT_PENDING"))
				.andExpect(jsonPath("$.error.message").value("요청이 만료됐어요"))
				.andExpect(jsonPath("$.error.details.status").value("EXPIRED"));
	}

	@Test
	void otherRecipientCannotCancel() throws Exception {
		String code = support.createdCode(support.signupAs("lrq-cother-g@example.com", "이영희", "GUARDIAN"));
		String recipient = support.signupAs("lrq-cother-r@example.com", "김하늘", "RECIPIENT");
		String requestId = support.sentRequestId(recipient, code);
		String stranger = support.signupAs("lrq-cother-s@example.com", "최민지", "RECIPIENT");

		support.decide(stranger, requestId, "cancel")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}
}
