package com.sonulim.domain.link.api;

import static com.sonulim.domain.link.api.LinkApiTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
import com.sonulim.global.push.LoggingPushSender;
import com.sonulim.global.push.PushMessage;
import com.sonulim.global.push.PushType;
import com.sonulim.support.MutableClock;

/**
 * 연결 확인(T-2): 요청·ack·결과 조회와 연결의 connection 반영.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(LinkCodeTestConfig.class)
@Transactional
class ConnectionCheckApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private MutableClock clock;

	@Autowired
	private ScriptedLinkCodeGenerator generator;

	@Autowired
	private LoggingPushSender pushSender;

	private LinkApiTestSupport support;

	@BeforeEach
	void setUp() {
		support = new LinkApiTestSupport(mockMvc, objectMapper);
		generator.reset();
		pushSender.clear();
	}

	// 상대 앱이 푸시를 받자마자 ack를 보낼 수 있게 checkId만 담는다(프론트 요청: data는 {type, checkId}).
	@Test
	void guardianRequestWakesRecipientDeviceWithCheckId() throws Exception {
		Linked linked = link("cc-push");

		String checkId = checkId(linked.guardian(), linked.linkId());

		assertThat(connectionCheckPushes()).singleElement().satisfies(push -> {
			assertThat(push.recipientUserId()).isEqualTo(UUID.fromString(support.userId(linked.recipient())));
			assertThat(push.data()).isEqualTo(Map.of("type", "CONNECTION_CHECK", "checkId", checkId));
		});
	}

	@Test
	void recipientRequestWakesGuardianDevice() throws Exception {
		Linked linked = link("cc-rpush");

		checkId(linked.recipient(), linked.linkId());

		assertThat(connectionCheckPushes()).singleElement().satisfies(push ->
				assertThat(push.recipientUserId()).isEqualTo(UUID.fromString(support.userId(linked.guardian()))));
	}

	// 진행 중인 확인을 돌려줄 때는 상대 기기를 다시 깨우지 않는다.
	@Test
	void reusedPendingCheckSendsNoSecondPush() throws Exception {
		Linked linked = link("cc-repush");
		checkId(linked.guardian(), linked.linkId());

		checkId(linked.recipient(), linked.linkId());
		checkId(linked.guardian(), linked.linkId());

		assertThat(connectionCheckPushes()).hasSize(1);
	}

	@Test
	void guardianRequestReturns202PendingWithThirtySecondTimeout() throws Exception {
		Linked linked = link("cc-req");

		String body = requestCheck(linked.guardian(), linked.linkId())
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.ok").value(true))
				.andExpect(jsonPath("$.data.id").isNotEmpty())
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.requestedBy").value(support.userId(linked.guardian())))
				.andExpect(jsonPath("$.data.confirmedAt").value(nullValue()))
				.andReturn().getResponse().getContentAsString();

		OffsetDateTime requestedAt = OffsetDateTime.parse(JsonPath.read(body, "$.data.requestedAt"));
		OffsetDateTime timeoutAt = OffsetDateTime.parse(JsonPath.read(body, "$.data.timeoutAt"));
		assertThat(Duration.between(requestedAt, timeoutAt)).isEqualTo(Duration.ofSeconds(30));
	}

	@Test
	void recipientCanRequestToo() throws Exception {
		Linked linked = link("cc-rreq");

		requestCheck(linked.recipient(), linked.linkId())
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.data.requestedBy").value(support.userId(linked.recipient())));
	}

	// 명세: 같은 연결에 PENDING 확인이 있으면 새로 만들지 않고 그것을 돌려준다(누가 요청했든).
	@Test
	void pendingCheckOfSameLinkIsReturnedInsteadOfNewOne() throws Exception {
		Linked linked = link("cc-reuse");
		String checkId = checkId(linked.guardian(), linked.linkId());

		requestCheck(linked.recipient(), linked.linkId())
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.data.id").value(checkId))
				.andExpect(jsonPath("$.data.requestedBy").value(support.userId(linked.guardian())));
	}

	@Test
	void ackWithinTimeoutConfirmsCheckAndLink() throws Exception {
		Linked linked = link("cc-ack");
		String checkId = checkId(linked.guardian(), linked.linkId());
		clock.advance(Duration.ofSeconds(2));

		ack(linked.recipient(), checkId)
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		String confirmedAt = JsonPath.read(getCheck(linked.guardian(), checkId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.data.confirmedAt").isNotEmpty())
				.andReturn().getResponse().getContentAsString(), "$.data.confirmedAt");
		support.getRequest(linked.guardian(), linked.requestId())
				.andExpect(jsonPath("$.data.link.connection.state").value("CONFIRMED"))
				.andExpect(jsonPath("$.data.link.connection.lastConfirmedAt").value(confirmedAt));
	}

	@Test
	void linkConnectionIsUnknownBeforeAnyCheck() throws Exception {
		Linked linked = link("cc-unknown");

		support.getRequest(linked.guardian(), linked.requestId())
				.andExpect(jsonPath("$.data.link.connection.state").value("UNKNOWN"))
				.andExpect(jsonPath("$.data.link.connection.lastConfirmedAt").value(nullValue()));
	}

	// 명세: timeoutAt이 지났는데 PENDING이면 조회 시점에 UNCONFIRMED로 바꿔 돌려준다.
	@Test
	void checkReadsAsUnconfirmedAfterTimeout() throws Exception {
		Linked linked = link("cc-timeout");
		String checkId = checkId(linked.guardian(), linked.linkId());
		clock.advance(Duration.ofSeconds(30));

		getCheck(linked.guardian(), checkId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("UNCONFIRMED"))
				.andExpect(jsonPath("$.data.confirmedAt").value(nullValue()));
		support.getRequest(linked.guardian(), linked.requestId())
				.andExpect(jsonPath("$.data.link.connection.state").value("UNCONFIRMED"))
				.andExpect(jsonPath("$.data.link.connection.lastConfirmedAt").value(nullValue()));
	}

	// 명세: 타임아웃 뒤에 와도 lastConfirmedAt은 갱신. 결과·연결 상태는 그대로(명세 해석, Pending Decisions).
	@Test
	void lateAckOnlyUpdatesLastConfirmedAt() throws Exception {
		Linked linked = link("cc-late");
		String checkId = checkId(linked.guardian(), linked.linkId());
		clock.advance(Duration.ofSeconds(31));

		ack(linked.recipient(), checkId).andExpect(status().isNoContent());

		getCheck(linked.guardian(), checkId)
				.andExpect(jsonPath("$.data.status").value("UNCONFIRMED"))
				.andExpect(jsonPath("$.data.confirmedAt").value(nullValue()));
		support.getRequest(linked.guardian(), linked.requestId())
				.andExpect(jsonPath("$.data.link.connection.state").value("UNCONFIRMED"))
				.andExpect(jsonPath("$.data.link.connection.lastConfirmedAt").isNotEmpty());
	}

	@Test
	void secondAckIsIgnored() throws Exception {
		Linked linked = link("cc-twice");
		String checkId = checkId(linked.guardian(), linked.linkId());
		ack(linked.recipient(), checkId).andExpect(status().isNoContent());
		String firstConfirmedAt = JsonPath.read(getCheck(linked.guardian(), checkId)
				.andReturn().getResponse().getContentAsString(), "$.data.confirmedAt");
		clock.advance(Duration.ofSeconds(5));

		ack(linked.recipient(), checkId).andExpect(status().isNoContent());

		getCheck(linked.guardian(), checkId)
				.andExpect(jsonPath("$.data.confirmedAt").value(firstConfirmedAt));
		support.getRequest(linked.guardian(), linked.requestId())
				.andExpect(jsonPath("$.data.link.connection.lastConfirmedAt").value(firstConfirmedAt));
	}

	@Test
	void newCheckIsCreatedAfterPreviousOneTimesOut() throws Exception {
		Linked linked = link("cc-new");
		String first = checkId(linked.guardian(), linked.linkId());
		clock.advance(Duration.ofSeconds(30));

		requestCheck(linked.guardian(), linked.linkId())
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.data.id").value(not(first)))
				.andExpect(jsonPath("$.data.status").value("PENDING"));
		getCheck(linked.guardian(), first)
				.andExpect(jsonPath("$.data.status").value("UNCONFIRMED"));
	}

	// "요청한 사람이 아닌 쪽만" — 요청한 사람 본인의 ack는 404(명세 해석, Pending Decisions).
	@Test
	void requesterCannotAckOwnCheck() throws Exception {
		Linked linked = link("cc-self");
		String checkId = checkId(linked.guardian(), linked.linkId());

		ack(linked.guardian(), checkId)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
		getCheck(linked.guardian(), checkId)
				.andExpect(jsonPath("$.data.status").value("PENDING"));
	}

	@Test
	void outsiderGets404Everywhere() throws Exception {
		Linked linked = link("cc-out");
		String checkId = checkId(linked.guardian(), linked.linkId());
		String outsider = support.signupAs("cc-out-x@example.com", "최민지", "RECIPIENT");

		requestCheck(outsider, linked.linkId()).andExpect(status().isNotFound());
		ack(outsider, checkId).andExpect(status().isNotFound());
		getCheck(outsider, checkId)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	// 명세에 없는 경우: 해제된 연결은 요청·ack·조회 모두 404(팀 결정, Pending Decisions).
	@Test
	void endedLinkGets404Everywhere() throws Exception {
		Linked linked = link("cc-ended");
		String checkId = checkId(linked.guardian(), linked.linkId());
		mockMvc.perform(delete("/api/v1/links/" + linked.linkId())
						.header(HttpHeaders.AUTHORIZATION, bearer(linked.guardian())))
				.andExpect(status().isNoContent());

		requestCheck(linked.guardian(), linked.linkId()).andExpect(status().isNotFound());
		ack(linked.recipient(), checkId).andExpect(status().isNotFound());
		getCheck(linked.guardian(), checkId)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void unknownOrMalformedIdsReturn404() throws Exception {
		Linked linked = link("cc-ids");

		requestCheck(linked.guardian(), UUID.randomUUID().toString()).andExpect(status().isNotFound());
		requestCheck(linked.guardian(), "lnk_1").andExpect(status().isNotFound());
		ack(linked.recipient(), "chk_9").andExpect(status().isNotFound());
		getCheck(linked.guardian(), UUID.randomUUID().toString()).andExpect(status().isNotFound());
	}

	private Linked link(String prefix) throws Exception {
		String guardian = support.signupAs(prefix + "-g@example.com", "이영희", "GUARDIAN");
		String recipient = support.signupAs(prefix + "-r@example.com", "김하늘", "RECIPIENT");
		String requestId = support.sentRequestId(recipient, support.createdCode(guardian));
		String body = support.decide(guardian, requestId, "approve")
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return new Linked(guardian, recipient, requestId, JsonPath.read(body, "$.data.link.id"));
	}

	private List<PushMessage> connectionCheckPushes() {
		return pushSender.sent().stream().filter(push -> push.type() == PushType.CONNECTION_CHECK).toList();
	}

	private String checkId(String accessToken, String linkId) throws Exception {
		String body = requestCheck(accessToken, linkId)
				.andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.id");
	}

	private ResultActions requestCheck(String accessToken, String linkId) throws Exception {
		return mockMvc.perform(post("/api/v1/links/" + linkId + "/connection-checks")
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken)));
	}

	private ResultActions ack(String accessToken, String checkId) throws Exception {
		return mockMvc.perform(post("/api/v1/connection-checks/" + checkId + "/ack")
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken)));
	}

	private ResultActions getCheck(String accessToken, String checkId) throws Exception {
		return mockMvc.perform(get("/api/v1/connection-checks/" + checkId)
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken)));
	}

	private record Linked(String guardian, String recipient, String requestId, String linkId) {
	}
}
