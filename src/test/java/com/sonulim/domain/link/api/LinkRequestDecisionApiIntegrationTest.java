package com.sonulim.domain.link.api;

import static com.sonulim.domain.link.api.LinkApiTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
import com.sonulim.domain.link.api.LinkCodeTestConfig.ScriptedLinkCodeGenerator;
import com.sonulim.domain.link.domain.LinkCodeRepository;
import com.sonulim.support.MutableClock;

/**
 * 보호자 쪽 연결 요청: 받은 요청 목록·승인·거절(L-3), 그리고 연결 뒤 ALREADY_LINKED.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(LinkCodeTestConfig.class)
@Transactional
class LinkRequestDecisionApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private MutableClock clock;

	@Autowired
	private ScriptedLinkCodeGenerator generator;

	@Autowired
	private LinkCodeRepository linkCodeRepository;

	private LinkApiTestSupport support;

	@BeforeEach
	void setUp() {
		support = new LinkApiTestSupport(mockMvc, objectMapper);
		generator.reset();
	}

	@Test
	void approvingCreatesActiveLink() throws Exception {
		String guardian = support.signupAs("lrd-approve-g@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String recipient = support.signupAs("lrd-approve-r@example.com", "김하늘", "RECIPIENT");
		String requestId = support.sentRequestId(recipient, code);

		support.decide(guardian, requestId, "approve")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("APPROVED"))
				.andExpect(jsonPath("$.data.decidedAt").isNotEmpty())
				.andExpect(jsonPath("$.data.link.id").isNotEmpty())
				.andExpect(jsonPath("$.data.link.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.link.guardian.id").value(support.userId(guardian)))
				.andExpect(jsonPath("$.data.link.guardian.name").value("이영희"))
				.andExpect(jsonPath("$.data.link.recipient.id").value(support.userId(recipient)))
				.andExpect(jsonPath("$.data.link.recipient.name").value("김하늘"))
				.andExpect(jsonPath("$.data.link.linkedAt").isNotEmpty())
				.andExpect(jsonPath("$.data.link.connection.state").value("UNKNOWN"))
				.andExpect(jsonPath("$.data.link.connection.lastConfirmedAt").value(nullValue()));

		// 당사자 대기 화면 폴링에서도 연결 결과가 보인다.
		support.getRequest(recipient, requestId)
				.andExpect(jsonPath("$.data.status").value("APPROVED"))
				.andExpect(jsonPath("$.data.link.status").value("ACTIVE"));
	}

	@Test
	void approvingMarksCodeUsed() throws Exception {
		String guardian = support.signupAs("lrd-used-g@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String recipient = support.signupAs("lrd-used-r@example.com", "김하늘", "RECIPIENT");
		String requestId = support.sentRequestId(recipient, code);

		support.decide(guardian, requestId, "approve").andExpect(status().isOk());

		assertThat(linkCodeRepository.findLatestNotRevokedByCode(code).orElseThrow().getUsedAt()).isNotNull();
	}

	@Test
	void cancelingAfterApprovalReturns409Approved() throws Exception {
		String guardian = support.signupAs("lrd-capp-g@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String recipient = support.signupAs("lrd-capp-r@example.com", "김하늘", "RECIPIENT");
		String requestId = support.sentRequestId(recipient, code);
		support.decide(guardian, requestId, "approve").andExpect(status().isOk());

		support.decide(recipient, requestId, "cancel")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("LINK_REQUEST_NOT_PENDING"))
				.andExpect(jsonPath("$.error.details.status").value("APPROVED"));
	}

	@Test
	void approvingExpiredRequestReturns409Expired() throws Exception {
		String guardian = support.signupAs("lrd-aexp-g@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String recipient = support.signupAs("lrd-aexp-r@example.com", "김하늘", "RECIPIENT");
		String requestId = support.sentRequestId(recipient, code);
		clock.advance(Duration.ofMinutes(10));

		support.decide(guardian, requestId, "approve")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("LINK_REQUEST_NOT_PENDING"))
				.andExpect(jsonPath("$.error.message").value("요청이 만료됐어요"))
				.andExpect(jsonPath("$.error.details.status").value("EXPIRED"));
	}

	@Test
	void approvingSecondRecipientAfterLinkingReturns409AlreadyLinked() throws Exception {
		String guardian = support.signupAs("lrd-second-g@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String first = support.sentRequestId(
				support.signupAs("lrd-second-r1@example.com", "김하늘", "RECIPIENT"), code);
		String second = support.sentRequestId(
				support.signupAs("lrd-second-r2@example.com", "최민지", "RECIPIENT"), code);
		support.decide(guardian, first, "approve").andExpect(status().isOk());

		support.decide(guardian, second, "approve")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ALREADY_LINKED"))
				.andExpect(jsonPath("$.error.message").value("이미 연결된 계정이 있어요"));
	}

	@Test
	void recipientCannotApprove() throws Exception {
		String code = support.createdCode(support.signupAs("lrd-rrole-g@example.com", "이영희", "GUARDIAN"));
		String recipient = support.signupAs("lrd-rrole-r@example.com", "김하늘", "RECIPIENT");
		String requestId = support.sentRequestId(recipient, code);

		support.decide(recipient, requestId, "approve")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void otherGuardianCannotApprove() throws Exception {
		String code = support.createdCode(support.signupAs("lrd-oth-g@example.com", "이영희", "GUARDIAN"));
		String requestId = support.sentRequestId(
				support.signupAs("lrd-oth-r@example.com", "김하늘", "RECIPIENT"), code);
		String otherGuardian = support.signupAs("lrd-oth-g2@example.com", "박지성", "GUARDIAN");

		support.decide(otherGuardian, requestId, "approve")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void rejectingKeepsCodeUsableForAnotherRecipient() throws Exception {
		String guardian = support.signupAs("lrd-reject-g@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String requestId = support.sentRequestId(
				support.signupAs("lrd-reject-r1@example.com", "김하늘", "RECIPIENT"), code);

		support.decide(guardian, requestId, "reject")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("REJECTED"))
				.andExpect(jsonPath("$.data.decidedAt").isNotEmpty())
				.andExpect(jsonPath("$.data.link").value(nullValue()));

		support.sendRequest(support.signupAs("lrd-reject-r2@example.com", "최민지", "RECIPIENT"), code)
				.andExpect(status().isCreated());
		assertThat(linkCodeRepository.findLatestNotRevokedByCode(code).orElseThrow().getUsedAt()).isNull();
	}

	@Test
	void rejectingCanceledRequestReturns409Canceled() throws Exception {
		String guardian = support.signupAs("lrd-rcan-g@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String recipient = support.signupAs("lrd-rcan-r@example.com", "김하늘", "RECIPIENT");
		String requestId = support.sentRequestId(recipient, code);
		support.decide(recipient, requestId, "cancel").andExpect(status().isOk());

		support.decide(guardian, requestId, "reject")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("LINK_REQUEST_NOT_PENDING"))
				.andExpect(jsonPath("$.error.details.status").value("CANCELED"));
	}

	@Test
	void listReturnsOwnRequestsNewestFirst() throws Exception {
		String guardian = support.signupAs("lrd-list-g@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String older = support.sentRequestId(support.signupAs("lrd-list-r1@example.com", "김하늘", "RECIPIENT"), code);
		clock.advance(Duration.ofSeconds(1));
		String newer = support.sentRequestId(support.signupAs("lrd-list-r2@example.com", "최민지", "RECIPIENT"), code);
		String otherCode = support.createdCode(support.signupAs("lrd-list-g2@example.com", "박지성", "GUARDIAN"));
		support.sentRequestId(support.signupAs("lrd-list-r3@example.com", "정우성", "RECIPIENT"), otherCode);

		list(guardian, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(2)))
				.andExpect(jsonPath("$.data.items[0].id").value(newer))
				.andExpect(jsonPath("$.data.items[1].id").value(older));
	}

	@Test
	void listWithoutStatusOnlyCoversLast24Hours() throws Exception {
		String guardian = support.signupAs("lrd-24h-g@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		support.sentRequestId(support.signupAs("lrd-24h-r1@example.com", "김하늘", "RECIPIENT"), code);
		clock.advance(Duration.ofHours(24).plusSeconds(1));
		guardian = support.login("lrd-24h-g@example.com");
		String recent = support.sentRequestId(support.signupAs("lrd-24h-r2@example.com", "최민지", "RECIPIENT"),
				support.createdCode(guardian));

		list(guardian, null)
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].id").value(recent));
		list(guardian, "EXPIRED")
				.andExpect(jsonPath("$.data.items", hasSize(1)));
	}

	@Test
	void pendingFilterLeavesOutExpiredRequests() throws Exception {
		String guardian = support.signupAs("lrd-pend-g@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		support.sentRequestId(support.signupAs("lrd-pend-r1@example.com", "김하늘", "RECIPIENT"), code);
		clock.advance(Duration.ofMinutes(10));
		String live = support.sentRequestId(support.signupAs("lrd-pend-r2@example.com", "최민지", "RECIPIENT"),
				support.createdCode(guardian));

		list(guardian, "PENDING")
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].id").value(live))
				.andExpect(jsonPath("$.data.items[0].status").value("PENDING"));
	}

	@Test
	void unknownStatusFilterReturns400() throws Exception {
		String guardian = support.signupAs("lrd-badst-g@example.com", "이영희", "GUARDIAN");

		list(guardian, "WAITING")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("status"));
	}

	@Test
	void recipientCannotList() throws Exception {
		String recipient = support.signupAs("lrd-lrole-r@example.com", "김하늘", "RECIPIENT");

		list(recipient, null)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	// MVP 1:1 — 연결된 보호자는 새 코드를 만들 수 없다.
	@Test
	void linkedGuardianCannotCreateCode() throws Exception {
		String guardian = support.signupAs("lrd-lcode-g@example.com", "이영희", "GUARDIAN");
		linkTogether(guardian, support.signupAs("lrd-lcode-r@example.com", "김하늘", "RECIPIENT"));

		support.createCode(guardian)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ALREADY_LINKED"));
	}

	@Test
	void codeOfLinkedGuardianReturnsAlreadyLinkedOnVerifyAndRequest() throws Exception {
		String guardian = support.signupAs("lrd-lg-g@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String requestId = support.sentRequestId(support.signupAs("lrd-lg-r1@example.com", "김하늘", "RECIPIENT"), code);
		support.decide(guardian, requestId, "approve").andExpect(status().isOk());
		String another = support.signupAs("lrd-lg-r2@example.com", "최민지", "RECIPIENT");

		verify(another, code)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ALREADY_LINKED"));
		support.sendRequest(another, code)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ALREADY_LINKED"));
	}

	@Test
	void linkedRecipientGetsAlreadyLinkedForAnotherGuardiansCode() throws Exception {
		String recipient = support.signupAs("lrd-lr-r@example.com", "김하늘", "RECIPIENT");
		linkTogether(support.signupAs("lrd-lr-g1@example.com", "이영희", "GUARDIAN"), recipient);
		String otherCode = support.createdCode(support.signupAs("lrd-lr-g2@example.com", "박지성", "GUARDIAN"));

		verify(recipient, otherCode)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ALREADY_LINKED"));
		support.sendRequest(recipient, otherCode)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ALREADY_LINKED"));
	}

	private void linkTogether(String guardian, String recipient) throws Exception {
		String requestId = support.sentRequestId(recipient, support.createdCode(guardian));
		support.decide(guardian, requestId, "approve").andExpect(status().isOk());
	}

	private ResultActions list(String accessToken, String statusFilter) throws Exception {
		var request = get("/api/v1/link-requests").header(HttpHeaders.AUTHORIZATION, bearer(accessToken));
		if (statusFilter != null) {
			request.param("status", statusFilter);
		}
		return mockMvc.perform(request);
	}

	private ResultActions verify(String accessToken, String code) throws Exception {
		return mockMvc.perform(post("/api/v1/link-codes/verify")
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("code", code))));
	}
}
