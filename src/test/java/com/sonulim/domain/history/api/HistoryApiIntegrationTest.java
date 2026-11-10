package com.sonulim.domain.history.api;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.messaging.domain.DeliveryFailureReason;
import com.sonulim.domain.messaging.domain.Message;
import com.sonulim.domain.messaging.domain.MessageKind;
import com.sonulim.domain.messaging.domain.MessageRepository;
import com.sonulim.domain.messaging.domain.SignalCategory;
import com.sonulim.domain.messaging.domain.SignalSnapshot;
import com.sonulim.domain.recipient.domain.HelpRequest;
import com.sonulim.domain.recipient.domain.HelpRequestRepository;
import com.sonulim.domain.recipient.domain.LocationStatus;
import com.sonulim.domain.recipient.domain.RecipientSignal;
import com.sonulim.domain.recipient.domain.RecipientSignalRepository;
import com.sonulim.domain.recipient.domain.RecipientSignalType;
import com.sonulim.global.security.JwtTokenProvider;
import com.sonulim.support.MutableClock;

/**
 * GET /links/{linkId}/history·/calendar. 기록은 messaging·recipient의 공개 팩토리로 만든다(그 코드는 고치지 않는다).
 * 시각은 한국 시각으로 적고 UTC로 바꿔 저장한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(HistoryApiIntegrationTest.ClockConfig.class)
@Transactional
class HistoryApiIntegrationTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final LocalDate DAY = LocalDate.of(2026, 10, 14);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider tokenProvider;

	@Autowired
	private MutableClock clock;

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private RecipientSignalRepository recipientSignalRepository;

	@Autowired
	private HelpRequestRepository helpRequestRepository;

	private UUID linkId;
	private UUID guardianId;
	private UUID recipientId;
	private String guardianToken;

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock(Instant.now());
		}
	}

	@BeforeEach
	void setUp() {
		linkId = UUID.randomUUID();
		guardianId = UUID.randomUUID();
		recipientId = UUID.randomUUID();
		guardianToken = tokenProvider.createAccessToken(guardianId, "GUARDIAN");
	}

	// 명세 응답 예시와 같은 하루: 보호자 신호(응답 없음)·도움 요청·보호자 부르기·전송 실패, 그리고 연습.
	@Test
	void listMergesAllKindsNewestFirstLikeSpecExample() throws Exception {
		Message noResponse = givenNoResponseSignal(kst(DAY, 18, 2, 1));
		HelpRequest helpRequest = helpRequestRepository.save(
				HelpRequest.send(linkId, recipientId, guardianId, kst(DAY, 17, 55, 2), LocationStatus.NOT_SHARED, null));
		helpRequest.markDelivered(kst(DAY, 17, 55, 3));
		helpRequest.markOpened(kst(DAY, 17, 55, 40));
		RecipientSignal call = recipientSignalRepository.save(
				RecipientSignal.send(linkId, recipientId, guardianId, RecipientSignalType.CALL_GUARDIAN,
						kst(DAY, 15, 20, 5)));
		call.markDelivered(kst(DAY, 15, 20, 6));
		Message failed = givenSignal(MessageKind.SIGNAL, kst(DAY, 13, 5, 0));
		failed.markFailed(DeliveryFailureReason.RECIPIENT_NO_DEVICE, kst(DAY, 13, 5, 0));
		Message practice = givenSignal(MessageKind.PRACTICE, kst(DAY, 12, 0, 0));
		practice.ack(kst(DAY, 12, 0, 2), kst(DAY, 12, 0, 2), 10);

		list(Map.of("date", DAY.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.date").value("2026-10-14"))
				.andExpect(jsonPath("$.data.hasPrevDate").value(false))
				.andExpect(jsonPath("$.data.items", hasSize(5)))
				// 보호자 신호 — 응답 없음
				.andExpect(jsonPath("$.data.items[0].id").value(noResponse.getId().toString()))
				.andExpect(jsonPath("$.data.items[0].kind").value("GUARDIAN_SIGNAL"))
				.andExpect(jsonPath("$.data.items[0].title").value("약 먹을 시간"))
				.andExpect(jsonPath("$.data.items[0].description").value("저녁 약을 먹자는 뜻"))
				.andExpect(jsonPath("$.data.items[0].isEmergency").value(false))
				.andExpect(jsonPath("$.data.items[0].occurredAt").value("2026-10-14T18:02:01+09:00"))
				.andExpect(jsonPath("$.data.items[0].delivery.status").value("DELIVERED"))
				.andExpect(jsonPath("$.data.items[0].delivery.at").value("2026-10-14T18:02:03+09:00"))
				.andExpect(jsonPath("$.data.items[0].delivery.failureReason").value(nullValue()))
				.andExpect(jsonPath("$.data.items[0].response.status").value("NO_RESPONSE"))
				.andExpect(jsonPath("$.data.items[0].response.at").value("2026-10-14T18:12:03+09:00"))
				.andExpect(jsonPath("$.data.items[0].openedAt").value(nullValue()))
				.andExpect(jsonPath("$.data.items[0].summaryStatus").value("NO_RESPONSE"))
				.andExpect(jsonPath("$.data.items[0].canResend").value(true))
				// 도움 요청
				.andExpect(jsonPath("$.data.items[1].id").value(helpRequest.getId().toString()))
				.andExpect(jsonPath("$.data.items[1].kind").value("HELP_REQUEST"))
				.andExpect(jsonPath("$.data.items[1].title").value("도움 요청"))
				.andExpect(jsonPath("$.data.items[1].description").value(nullValue()))
				.andExpect(jsonPath("$.data.items[1].isEmergency").value(true))
				.andExpect(jsonPath("$.data.items[1].delivery.status").value("DELIVERED"))
				.andExpect(jsonPath("$.data.items[1].delivery.at").value("2026-10-14T17:55:03+09:00"))
				.andExpect(jsonPath("$.data.items[1]", hasKey("response")))
				.andExpect(jsonPath("$.data.items[1].response").value(nullValue()))
				.andExpect(jsonPath("$.data.items[1].openedAt").value("2026-10-14T17:55:40+09:00"))
				.andExpect(jsonPath("$.data.items[1].summaryStatus").value("DELIVERED_ONLY"))
				.andExpect(jsonPath("$.data.items[1].canResend").value(false))
				// 보호자 부르기
				.andExpect(jsonPath("$.data.items[2].id").value(call.getId().toString()))
				.andExpect(jsonPath("$.data.items[2].kind").value("RECIPIENT_SIGNAL"))
				.andExpect(jsonPath("$.data.items[2].title").value("보호자 부르기"))
				.andExpect(jsonPath("$.data.items[2].isEmergency").value(false))
				.andExpect(jsonPath("$.data.items[2].delivery.at").value("2026-10-14T15:20:06+09:00"))
				.andExpect(jsonPath("$.data.items[2].openedAt").value(nullValue()))
				.andExpect(jsonPath("$.data.items[2].summaryStatus").value("DELIVERED_ONLY"))
				// 전송 실패
				.andExpect(jsonPath("$.data.items[3].id").value(failed.getId().toString()))
				.andExpect(jsonPath("$.data.items[3].delivery.status").value("FAILED"))
				.andExpect(jsonPath("$.data.items[3].delivery.at").value("2026-10-14T13:05:00+09:00"))
				.andExpect(jsonPath("$.data.items[3].delivery.failureReason").value("RECIPIENT_NO_DEVICE"))
				.andExpect(jsonPath("$.data.items[3].response").value(nullValue()))
				.andExpect(jsonPath("$.data.items[3].summaryStatus").value("FAILED"))
				.andExpect(jsonPath("$.data.items[3].canResend").value(true))
				// 연습
				.andExpect(jsonPath("$.data.items[4].id").value(practice.getId().toString()))
				.andExpect(jsonPath("$.data.items[4].kind").value("PRACTICE"))
				.andExpect(jsonPath("$.data.items[4].response").value(nullValue()))
				.andExpect(jsonPath("$.data.items[4].summaryStatus").value("DELIVERED_ONLY"))
				.andExpect(jsonPath("$.data.items[4].canResend").value(false));
	}

	@Test
	void unconfirmedSignalIsMappedFromMessaging() throws Exception {
		Message unconfirmed = givenSignal(MessageKind.SIGNAL, kst(DAY, 9, 0, 0));
		unconfirmed.markUnconfirmed(kst(DAY, 9, 1, 0));

		list(Map.of("date", DAY.toString()))
				.andExpect(jsonPath("$.data.items[0].delivery.status").value("UNCONFIRMED"))
				.andExpect(jsonPath("$.data.items[0].delivery.at").value("2026-10-14T09:01:00+09:00"))
				.andExpect(jsonPath("$.data.items[0].summaryStatus").value("UNCONFIRMED"))
				.andExpect(jsonPath("$.data.items[0].canResend").value(true));
	}

	@Test
	void noResponseFilterKeepsOnlyGuardianSignalsWithoutResponse() throws Exception {
		Message noResponse = givenNoResponseSignal(kst(DAY, 18, 2, 1));
		givenSignal(MessageKind.SIGNAL, kst(DAY, 13, 5, 0))
				.markFailed(DeliveryFailureReason.PUSH_PROVIDER_ERROR, kst(DAY, 13, 5, 0));
		recipientSignalRepository.save(RecipientSignal.send(linkId, recipientId, guardianId,
				RecipientSignalType.CALL_GUARDIAN, kst(DAY, 15, 20, 5)));

		list(Map.of("date", DAY.toString(), "filter", "NO_RESPONSE"))
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].id").value(noResponse.getId().toString()));
	}

	@Test
	void emptyDayReturnsEmptyItemsAndHasPrevDateWhenEarlierRecordExists() throws Exception {
		givenSignal(MessageKind.SIGNAL, kst(DAY, 10, 0, 0));

		list(Map.of("date", DAY.plusDays(1).toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.date").value("2026-10-15"))
				.andExpect(jsonPath("$.data.hasPrevDate").value(true))
				.andExpect(jsonPath("$.data.items", hasSize(0)));
		list(Map.of("date", DAY.minusDays(1).toString()))
				.andExpect(jsonPath("$.data.hasPrevDate").value(false));
	}

	// 하루는 tz 기준 00:00~24:00. 한국 10/14 00:30은 UTC로 10/13 15:30이다.
	@Test
	void dayBoundaryFollowsTz() throws Exception {
		Message justAfterMidnight = givenSignal(MessageKind.SIGNAL, kst(DAY, 0, 30, 0));
		String id = justAfterMidnight.getId().toString();

		list(Map.of("date", "2026-10-14"))
				.andExpect(jsonPath("$.data.items[0].id").value(id));
		list(Map.of("date", "2026-10-13"))
				.andExpect(jsonPath("$.data.items", hasSize(0)));
		list(Map.of("date", "2026-10-13", "tz", "UTC"))
				.andExpect(jsonPath("$.data.items[0].id").value(id))
				// 응답 시각은 tz와 관계없이 +09:00(공통 규칙)
				.andExpect(jsonPath("$.data.items[0].occurredAt").value("2026-10-14T00:30:00+09:00"));
		list(Map.of("date", "2026-10-14", "tz", "UTC"))
				.andExpect(jsonPath("$.data.items", hasSize(0)));
	}

	@Test
	void dateDefaultsToTodayInTz() throws Exception {
		LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
		Message today = givenSignal(MessageKind.SIGNAL, now);

		list(Map.of())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.date").value(clock.instant().atZone(SEOUL).toLocalDate().toString()))
				.andExpect(jsonPath("$.data.items[0].id").value(today.getId().toString()));
	}

	@Test
	void futureDateReturns400() throws Exception {
		LocalDate tomorrow = clock.instant().atZone(SEOUL).toLocalDate().plusDays(1);

		list(Map.of("date", tomorrow.toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("date"));
	}

	@Test
	void malformedQueryValuesReturn400WithField() throws Exception {
		list(Map.of("date", "2026/10/14"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.field").value("date"));
		list(Map.of("filter", "WAITING"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.field").value("filter"));
		list(Map.of("tz", "Mars/Olympus"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.field").value("tz"));
	}

	@Test
	void onlyThisGuardiansRecordsOfThisLinkAreListed() throws Exception {
		Message mine = givenSignal(MessageKind.SIGNAL, kst(DAY, 10, 0, 0));
		messageRepository.save(Message.send(linkId, UUID.randomUUID(), recipientId, MessageKind.SIGNAL, snapshot(),
				kst(DAY, 11, 0, 0), Duration.ofSeconds(60)));
		messageRepository.save(Message.send(UUID.randomUUID(), guardianId, recipientId, MessageKind.SIGNAL,
				snapshot(), kst(DAY, 12, 0, 0), Duration.ofSeconds(60)));

		list(Map.of("date", DAY.toString()))
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].id").value(mine.getId().toString()));
	}

	// 이 브랜치에는 Link가 없어 연결 존재를 확인할 수 없다 — 없는 연결은 지금 빈 목록이다(임시, history.md 후속 작업).
	@Test
	void unknownLinkCurrentlyReturnsEmptyListUntilLinkCheckIsAdded() throws Exception {
		mockMvc.perform(get("/api/v1/links/{linkId}/history", UUID.randomUUID())
						.param("date", DAY.toString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(0)));
	}

	@Test
	void malformedLinkIdReturns404() throws Exception {
		mockMvc.perform(get("/api/v1/links/lnk_1/history").header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void recipientOrRolelessUserIsRejected() throws Exception {
		mockMvc.perform(get("/api/v1/links/{linkId}/history", linkId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.createAccessToken(recipientId,
								"RECIPIENT")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
		mockMvc.perform(get("/api/v1/links/{linkId}/history/calendar", linkId)
						.param("month", "2026-10")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.createAccessToken(guardianId, null)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_REQUIRED"));
	}

	@Test
	void calendarListsOnlyDaysWithRecordsAndFlagsNoResponse() throws Exception {
		givenSignal(MessageKind.SIGNAL, kst(LocalDate.of(2026, 10, 3), 9, 0, 0));
		givenNoResponseSignal(kst(DAY, 18, 2, 1));
		givenSignal(MessageKind.PRACTICE, kst(DAY, 12, 0, 0));
		recipientSignalRepository.save(RecipientSignal.send(linkId, recipientId, guardianId,
				RecipientSignalType.CALL_GUARDIAN, kst(DAY, 15, 20, 5)));
		// 한국 11/1 00:30 = UTC 10/31 15:30 — 한국 기준으로는 11월이다.
		givenSignal(MessageKind.SIGNAL, kst(LocalDate.of(2026, 11, 1), 0, 30, 0));

		calendar("2026-10", null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.month").value("2026-10"))
				.andExpect(jsonPath("$.data.days", hasSize(2)))
				.andExpect(jsonPath("$.data.days[0].date").value("2026-10-03"))
				.andExpect(jsonPath("$.data.days[0].count").value(1))
				.andExpect(jsonPath("$.data.days[0].hasNoResponse").value(false))
				.andExpect(jsonPath("$.data.days[1].date").value("2026-10-14"))
				.andExpect(jsonPath("$.data.days[1].count").value(3))
				.andExpect(jsonPath("$.data.days[1].hasNoResponse").value(true));
		calendar("2026-10", "UTC")
				.andExpect(jsonPath("$.data.days", hasSize(3)))
				.andExpect(jsonPath("$.data.days[2].date").value("2026-10-31"));
	}

	@Test
	void calendarRequiresValidMonth() throws Exception {
		calendar(null, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("month"));
		calendar("2026-13", null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.field").value("month"));
	}

	@Test
	void calendarWithoutRecordsReturnsEmptyDays() throws Exception {
		calendar("2026-09", null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.days", hasSize(0)));
	}

	private Message givenNoResponseSignal(LocalDateTime requestedAt) {
		Message message = givenSignal(MessageKind.SIGNAL, requestedAt);
		message.ack(requestedAt.plusSeconds(2), requestedAt.plusSeconds(2), 10);
		message.markNoResponse(requestedAt.plusSeconds(2).plusMinutes(10));
		return message;
	}

	private Message givenSignal(MessageKind kind, LocalDateTime requestedAt) {
		return messageRepository.save(Message.send(linkId, guardianId, recipientId, kind, snapshot(), requestedAt,
				Duration.ofSeconds(60)));
	}

	private static SignalSnapshot snapshot() {
		return new SignalSnapshot(UUID.randomUUID(), "약 먹을 시간", "저녁 약을 먹자는 뜻", SignalCategory.DAILY, "pill",
				List.of("SHORT", "SHORT", "LONG"), List.of(150, 150, 150, 150, 1000), 1900);
	}

	// 한국 시각 → 저장 기준인 UTC LocalDateTime
	private static LocalDateTime kst(LocalDate day, int hour, int minute, int second) {
		return day.atTime(hour, minute, second).atZone(SEOUL).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
	}

	private ResultActions list(Map<String, String> params) throws Exception {
		var request = get("/api/v1/links/{linkId}/history", linkId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken);
		params.forEach(request::param);
		return mockMvc.perform(request);
	}

	private ResultActions calendar(String month, String tz) throws Exception {
		var request = get("/api/v1/links/{linkId}/history/calendar", linkId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken);
		if (month != null) {
			request.param("month", month);
		}
		if (tz != null) {
			request.param("tz", tz);
		}
		return mockMvc.perform(request);
	}
}
