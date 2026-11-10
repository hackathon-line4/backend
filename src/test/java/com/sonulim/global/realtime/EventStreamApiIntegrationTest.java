package com.sonulim.global.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.async.WebAsyncUtils;

import com.sonulim.global.security.JwtTokenProvider;
import com.sonulim.support.MutableClock;

/**
 * GET /api/v1/events. 사용자 행이 필요 없어서 가입 없이 토큰만 만들어 쓴다 — 그래서 @Transactional을 붙이지 않는다
 * (테스트 트랜잭션 안에서 발행하면 커밋이 없어 이벤트가 나가지 않는다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EventStreamApiIntegrationTest.ClockConfig.class)
class EventStreamApiIntegrationTest {

	private static final Pattern EVENT_ID = Pattern.compile("^id:(\\S+)$", Pattern.MULTILINE);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SseEventHub hub;

	@Autowired
	private JwtTokenProvider tokenProvider;

	@Autowired
	private MutableClock clock;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock(Instant.now());
		}
	}

	@Test
	void connectingWithoutTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/v1/events").accept(MediaType.TEXT_EVENT_STREAM))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void connectingWithExpiredTokenReturns401TokenExpired() throws Exception {
		String token = tokenProvider.createAccessToken(UUID.randomUUID(), "GUARDIAN");
		clock.advance(Duration.ofHours(1).plusSeconds(1));

		connectRequest(token, null)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"))
				.andExpect(jsonPath("$.error.message").value("토큰이 만료되었습니다"));
	}

	@Test
	void connectingOpensEventStream() throws Exception {
		MvcResult result = connect(token(UUID.randomUUID()), null);

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
	}

	// 액세스 토큰이 만료되는 시각에 연결이 닫히도록 연결 timeout을 남은 시간으로 둔다.
	@Test
	void connectionTimesOutWhenAccessTokenExpires() throws Exception {
		MvcResult result = connect(token(UUID.randomUUID()), null);

		long timeoutMs = result.getRequest().getAsyncContext().getTimeout();
		// 토큰 만료 시각은 초 단위로 잘리므로 1시간보다 최대 1초 짧다.
		assertThat(timeoutMs).isBetween(Duration.ofHours(1).minusSeconds(1).toMillis(), Duration.ofHours(1).toMillis());
	}

	@Test
	void publishedEventArrivesWithUlidIdNameAndJsonData() throws Exception {
		UUID userId = UUID.randomUUID();
		MvcResult result = connect(token(userId), null);

		hub.publish(userId, RealtimeEventType.LINK_REQUEST_UPDATED, Map.of("linkRequest", Map.of("status", "승인")));

		String body = body(result);
		List<String> ids = eventIds(body);
		assertThat(ids).hasSize(1);
		assertThat(ids.get(0)).hasSize(26);
		assertThat(body).contains("event:link_request.updated\n")
				.contains("data:{\"linkRequest\":{\"status\":\"승인\"}}\n");
	}

	@Test
	void eventsForOtherUsersAreNotSent() throws Exception {
		MvcResult result = connect(token(UUID.randomUUID()), null);

		hub.publish(UUID.randomUUID(), RealtimeEventType.MESSAGE_CREATED, Map.of("id", "x"));

		assertThat(body(result)).doesNotContain("event:");
	}

	@Test
	void eventIdsIncreaseInPublishOrder() throws Exception {
		UUID userId = UUID.randomUUID();
		MvcResult result = connect(token(userId), null);

		hub.publish(userId, RealtimeEventType.MESSAGE_UPDATED, Map.of("n", 1));
		hub.publish(userId, RealtimeEventType.MESSAGE_UPDATED, Map.of("n", 2));
		hub.publish(userId, RealtimeEventType.MESSAGE_UPDATED, Map.of("n", 3));

		List<String> ids = eventIds(body(result));
		assertThat(ids).hasSize(3).isSorted();
	}

	// 응답 헤더가 바로 나가도록 연결하자마자 ping을 한 번 보낸다.
	@Test
	void connectionStartsWithPing() throws Exception {
		MvcResult result = connect(token(UUID.randomUUID()), null);

		assertThat(body(result)).startsWith(": ping\n");
	}

	@Test
	void periodicPingIsSentAsComment() throws Exception {
		MvcResult result = connect(token(UUID.randomUUID()), null);

		hub.pingAll();

		assertThat(body(result).split(": ping\n", -1)).hasSize(3);
	}

	// 기기 1대: 같은 사용자가 다시 연결하면 이전 연결을 닫고, 이벤트는 새 연결로만 간다.
	@Test
	void reconnectingClosesPreviousConnection() throws Exception {
		UUID userId = UUID.randomUUID();
		MvcResult first = connect(token(userId), null);
		MvcResult second = connect(token(userId), null);

		hub.publish(userId, RealtimeEventType.LINK_UPDATED, Map.of("id", "x"));

		// 닫힌 연결은 비동기 결과가 정해져 디스패치를 기다린다(MockMvc는 isAsyncStarted를 그대로 둔다).
		assertThat(WebAsyncUtils.getAsyncManager(first.getRequest()).hasConcurrentResult()).isTrue();
		assertThat(WebAsyncUtils.getAsyncManager(second.getRequest()).hasConcurrentResult()).isFalse();
		assertThat(body(first)).doesNotContain("event:");
		assertThat(body(second)).contains("event:link.updated");
	}

	// 닫힌 연결의 비동기 디스패치(ASYNC)도 인증 오류 없이 끝나야 한다.
	@Test
	void closedConnectionFinishesAsyncDispatchWithoutAuthError() throws Exception {
		UUID userId = UUID.randomUUID();
		MvcResult first = connect(token(userId), null);
		connect(token(userId), null);

		mockMvc.perform(asyncDispatch(first))
				.andExpect(status().isOk());
	}

	@Test
	void reconnectingWithLastEventIdReplaysMissedEvents() throws Exception {
		UUID userId = UUID.randomUUID();
		MvcResult first = connect(token(userId), null);
		hub.publish(userId, RealtimeEventType.MESSAGE_UPDATED, Map.of("n", 1));
		String lastReceived = eventIds(body(first)).get(0);
		hub.publish(userId, RealtimeEventType.MESSAGE_UPDATED, Map.of("n", 2));
		hub.publish(userId, RealtimeEventType.MESSAGE_UPDATED, Map.of("n", 3));

		String replayed = body(connect(token(userId), lastReceived));

		assertThat(replayed).doesNotContain("data:{\"n\":1}")
				.contains("data:{\"n\":2}")
				.contains("data:{\"n\":3}");
		assertThat(replayed.indexOf("data:{\"n\":2}")).isLessThan(replayed.indexOf("data:{\"n\":3}"));
		assertThat(eventIds(replayed)).doesNotContain(lastReceived);
	}

	@Test
	void reconnectingWithoutLastEventIdReplaysNothing() throws Exception {
		UUID userId = UUID.randomUUID();
		connect(token(userId), null);
		hub.publish(userId, RealtimeEventType.MESSAGE_UPDATED, Map.of("n", 1));

		assertThat(body(connect(token(userId), null))).doesNotContain("event:");
	}

	@Test
	void lastEventIdOlderThanFiveMinutesReplaysNothing() throws Exception {
		UUID userId = UUID.randomUUID();
		MvcResult first = connect(token(userId), null);
		hub.publish(userId, RealtimeEventType.MESSAGE_UPDATED, Map.of("n", 1));
		String lastReceived = eventIds(body(first)).get(0);
		clock.advance(Duration.ofMinutes(5).plusSeconds(1));
		hub.publish(userId, RealtimeEventType.MESSAGE_UPDATED, Map.of("n", 2));

		assertThat(body(connect(token(userId), lastReceived))).doesNotContain("event:");
	}

	@Test
	void eventIsSentOnlyAfterCommit() throws Exception {
		UUID userId = UUID.randomUUID();
		MvcResult result = connect(token(userId), null);
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);

		transaction.executeWithoutResult(status -> {
			hub.publish(userId, RealtimeEventType.MESSAGE_UPDATED, Map.of("n", 1));
			assertThat(body(result)).doesNotContain("event:");
		});

		assertThat(body(result)).contains("data:{\"n\":1}");
	}

	@Test
	void eventIsNotSentWhenTransactionRollsBack() throws Exception {
		UUID userId = UUID.randomUUID();
		MvcResult result = connect(token(userId), null);
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);

		transaction.executeWithoutResult(status -> {
			hub.publish(userId, RealtimeEventType.MESSAGE_UPDATED, Map.of("n", 1));
			status.setRollbackOnly();
		});

		assertThat(body(result)).doesNotContain("event:");
	}

	private String token(UUID userId) {
		return tokenProvider.createAccessToken(userId, "GUARDIAN");
	}

	private MvcResult connect(String token, String lastEventId) throws Exception {
		return connectRequest(token, lastEventId)
				.andExpect(request().asyncStarted())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
				.andReturn();
	}

	private org.springframework.test.web.servlet.ResultActions connectRequest(String token, String lastEventId)
			throws Exception {
		var request = get("/api/v1/events")
				.accept(MediaType.TEXT_EVENT_STREAM)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		if (lastEventId != null) {
			request.header("Last-Event-ID", lastEventId);
		}
		return mockMvc.perform(request);
	}

	private static String body(MvcResult result) {
		try {
			return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		} catch (java.io.UnsupportedEncodingException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static List<String> eventIds(String body) {
		Matcher matcher = EVENT_ID.matcher(body);
		return matcher.results().map(match -> match.group(1)).toList();
	}
}
