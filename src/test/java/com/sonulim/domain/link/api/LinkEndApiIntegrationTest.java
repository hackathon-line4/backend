package com.sonulim.domain.link.api;

import static com.sonulim.domain.link.api.LinkApiTestSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

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

/**
 * 연결 해제(L-4)와, 해제 뒤 승인에 쓴 코드를 다시 쓸 수 없는지.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(LinkCodeTestConfig.class)
@Transactional
class LinkEndApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ScriptedLinkCodeGenerator generator;

	private LinkApiTestSupport support;

	@BeforeEach
	void setUp() {
		support = new LinkApiTestSupport(mockMvc, objectMapper);
		generator.reset();
	}

	@Test
	void guardianEndsLinkAndRequestShowsEndedLink() throws Exception {
		String guardian = support.signupAs("lnk-end-g@example.com", "이영희", "GUARDIAN");
		String recipient = support.signupAs("lnk-end-r@example.com", "김하늘", "RECIPIENT");
		String requestId = support.sentRequestId(recipient, support.createdCode(guardian));
		String linkId = approvedLinkId(guardian, requestId);

		end(guardian, linkId)
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		support.getRequest(recipient, requestId)
				.andExpect(jsonPath("$.data.status").value("APPROVED"))
				.andExpect(jsonPath("$.data.link.id").value(linkId))
				.andExpect(jsonPath("$.data.link.status").value("ENDED"));
	}

	// 명세에 없는 경우: 재시도해도 결과(해제됨)가 같으므로 다시 204(docs/domains/link.md Pending Decisions).
	@Test
	void endingAlreadyEndedLinkReturns204Again() throws Exception {
		String guardian = support.signupAs("lnk-twice-g@example.com", "이영희", "GUARDIAN");
		String linkId = link(guardian, support.signupAs("lnk-twice-r@example.com", "김하늘", "RECIPIENT"));
		end(guardian, linkId).andExpect(status().isNoContent());

		end(guardian, linkId).andExpect(status().isNoContent());
	}

	@Test
	void bothPeopleCanLinkAgainAfterEnding() throws Exception {
		String guardian = support.signupAs("lnk-again-g@example.com", "이영희", "GUARDIAN");
		String recipient = support.signupAs("lnk-again-r@example.com", "김하늘", "RECIPIENT");
		end(guardian, link(guardian, recipient)).andExpect(status().isNoContent());

		String requestId = support.sentRequestId(recipient, support.createdCode(guardian));
		support.decide(guardian, requestId, "approve")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.link.status").value("ACTIVE"));
	}

	@Test
	void recipientCannotEndLink() throws Exception {
		String guardian = support.signupAs("lnk-rrole-g@example.com", "이영희", "GUARDIAN");
		String recipient = support.signupAs("lnk-rrole-r@example.com", "김하늘", "RECIPIENT");
		String linkId = link(guardian, recipient);

		end(recipient, linkId)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void otherGuardianGets404() throws Exception {
		String linkId = link(support.signupAs("lnk-oth-g@example.com", "이영희", "GUARDIAN"),
				support.signupAs("lnk-oth-r@example.com", "김하늘", "RECIPIENT"));
		String otherGuardian = support.signupAs("lnk-oth-g2@example.com", "박지성", "GUARDIAN");

		end(otherGuardian, linkId)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void unknownOrMalformedLinkIdReturns404() throws Exception {
		String guardian = support.signupAs("lnk-unknown-g@example.com", "이영희", "GUARDIAN");

		end(guardian, UUID.randomUUID().toString())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
		end(guardian, "lnk_1")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void endingWithoutTokenReturns401() throws Exception {
		mockMvc.perform(delete("/api/v1/links/" + UUID.randomUUID()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	// 명세에 없는 보수적 결정: 승인에 쓴 코드는 해제 뒤 만료 전이어도 다시 쓸 수 없다(docs/domains/link.md Pending Decisions).
	@Test
	void codeUsedForApprovalIsNotFoundAfterEnding() throws Exception {
		String guardian = support.signupAs("lnk-used-g@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String requestId = support.sentRequestId(support.signupAs("lnk-used-r1@example.com", "김하늘", "RECIPIENT"), code);
		end(guardian, approvedLinkId(guardian, requestId)).andExpect(status().isNoContent());
		String another = support.signupAs("lnk-used-r2@example.com", "최민지", "RECIPIENT");

		verify(another, code)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("LINK_CODE_NOT_FOUND"))
				.andExpect(jsonPath("$.error.field").value("code"));
		support.sendRequest(another, code)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("LINK_CODE_NOT_FOUND"));
	}

	// 거절은 코드를 쓰지 않으므로, 만료 전이면 다른 당사자가 코드 확인도 할 수 있다.
	@Test
	void codeOfRejectedRequestStillVerifies() throws Exception {
		String guardian = support.signupAs("lnk-rej-g@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String requestId = support.sentRequestId(support.signupAs("lnk-rej-r1@example.com", "김하늘", "RECIPIENT"), code);
		support.decide(guardian, requestId, "reject").andExpect(status().isOk());

		verify(support.signupAs("lnk-rej-r2@example.com", "최민지", "RECIPIENT"), code)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.guardian.name").value("이영희"));
	}

	private String link(String guardian, String recipient) throws Exception {
		return approvedLinkId(guardian, support.sentRequestId(recipient, support.createdCode(guardian)));
	}

	private String approvedLinkId(String guardian, String requestId) throws Exception {
		String body = support.decide(guardian, requestId, "approve")
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.link.id");
	}

	private ResultActions end(String accessToken, String linkId) throws Exception {
		return mockMvc.perform(delete("/api/v1/links/" + linkId).header(HttpHeaders.AUTHORIZATION, bearer(accessToken)));
	}

	private ResultActions verify(String accessToken, String code) throws Exception {
		return mockMvc.perform(post("/api/v1/link-codes/verify")
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("code", code))));
	}
}
