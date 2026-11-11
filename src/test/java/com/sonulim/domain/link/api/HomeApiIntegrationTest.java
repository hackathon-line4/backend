package com.sonulim.domain.link.api;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
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

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.account.domain.UserRepository;
import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkRepository;
import com.sonulim.domain.messaging.domain.Message;
import com.sonulim.domain.messaging.domain.MessageKind;
import com.sonulim.domain.messaging.domain.MessageRepository;
import com.sonulim.domain.messaging.domain.SignalCategory;
import com.sonulim.domain.messaging.domain.SignalSnapshot;
import com.sonulim.domain.recipient.domain.HelpRequest;
import com.sonulim.domain.recipient.domain.HelpRequestRepository;
import com.sonulim.domain.recipient.domain.LocationStatus;
import com.sonulim.domain.signal.domain.FavoriteSignal;
import com.sonulim.domain.signal.domain.FavoriteSignalRepository;
import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.global.security.JwtTokenProvider;
import com.sonulim.support.MutableClock;

/**
 * 홈 요약(S-4, #81). 기록·신호는 각 도메인 리포지토리로 테스트 데이터만 만들고, 읽기는 API로 확인한다.
 * favoriteSignals 테스트는 signal의 FavoriteSignalQueryService(#81 signal 커밋)에 기댄다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(HomeApiIntegrationTest.ClockConfig.class)
@Transactional
class HomeApiIntegrationTest {

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock(Instant.now());
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private JwtTokenProvider tokenProvider;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private LinkRepository linkRepository;

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private HelpRequestRepository helpRequestRepository;

	@Autowired
	private SignalRepository signalRepository;

	@Autowired
	private FavoriteSignalRepository favoriteSignalRepository;

	private User guardian;
	private User recipient;
	private Link link;
	private String guardianToken;

	@BeforeEach
	void setUp() {
		guardian = saveUser("home-g-" + UUID.randomUUID() + "@example.com", "이영희", Role.GUARDIAN);
		recipient = saveUser("home-r-" + UUID.randomUUID() + "@example.com", "김하늘", Role.RECIPIENT);
		link = linkRepository.saveAndFlush(Link.connect(guardian.getId(), recipient.getId(), now()));
		guardianToken = tokenProvider.createAccessToken(guardian.getId(), "GUARDIAN");
	}

	@Test
	void newLinkShowsRecipientAndEmptyState() throws Exception {
		home(link.getId(), guardianToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.recipient.id").value(recipient.getId().toString()))
				.andExpect(jsonPath("$.data.recipient.name").value("김하늘"))
				.andExpect(jsonPath("$.data.recipient.initial").value("김"))
				.andExpect(jsonPath("$.data.recipient.connection.state").value("UNKNOWN"))
				.andExpect(jsonPath("$.data.recipient.connection.lastConfirmedAt").value(nullValue()))
				.andExpect(jsonPath("$.data.recipient.status.type").value("OK"))
				.andExpect(jsonPath("$.data.recipient.status.messageId").value(nullValue()))
				.andExpect(jsonPath("$.data.recipient.status.since").value(nullValue()))
				.andExpect(jsonPath("$.data.recipient.status.elapsedMinutes").value(nullValue()))
				.andExpect(jsonPath("$.data.recipient", hasKey("lastResponse")))
				.andExpect(jsonPath("$.data.recipient.lastResponse").value(nullValue()))
				.andExpect(jsonPath("$.data.favoriteSignals", hasSize(0)))
				.andExpect(jsonPath("$.data.location.sharingEnabled").value(false))
				.andExpect(jsonPath("$.data.location", hasKey("last")))
				.andExpect(jsonPath("$.data.location.last").value(nullValue()))
				.andExpect(jsonPath("$.data.recentActivities", hasSize(0)));
	}

	@Test
	void waitingSignalShowsElapsedMinutes() throws Exception {
		Message waiting = deliveredSignal("약 먹을 시간", now().minusMinutes(12));

		home(link.getId(), guardianToken)
				.andExpect(jsonPath("$.data.recipient.status.type").value("WAITING"))
				.andExpect(jsonPath("$.data.recipient.status.messageId").value(waiting.getId().toString()))
				.andExpect(jsonPath("$.data.recipient.status.signalName").value("약 먹을 시간"))
				.andExpect(jsonPath("$.data.recipient.status.since").isNotEmpty())
				.andExpect(jsonPath("$.data.recipient.status.elapsedMinutes").value(12));
	}

	@Test
	void noResponseOfLatestSignalAndLastResponseOfEarlierOne() throws Exception {
		Message responded = deliveredSignal("밥 먹자", now().minusMinutes(40));
		responded.respond(now().minusMinutes(39));
		Message noResponse = deliveredSignal("약 먹을 시간", now().minusMinutes(20));
		noResponse.markNoResponse(now().minusMinutes(10));

		home(link.getId(), guardianToken)
				.andExpect(jsonPath("$.data.recipient.status.type").value("NO_RESPONSE"))
				.andExpect(jsonPath("$.data.recipient.status.messageId").value(noResponse.getId().toString()))
				.andExpect(jsonPath("$.data.recipient.status.elapsedMinutes").value(20))
				.andExpect(jsonPath("$.data.recipient.lastResponse.messageId").value(responded.getId().toString()))
				.andExpect(jsonPath("$.data.recipient.lastResponse.signalName").value("밥 먹자"))
				.andExpect(jsonPath("$.data.recipient.lastResponse.respondedAt").isNotEmpty());
	}

	@Test
	void unopenedHelpRequestWinsAndOpenedOneDoesNot() throws Exception {
		Message noResponse = deliveredSignal("약 먹을 시간", now().minusMinutes(20));
		noResponse.markNoResponse(now().minusMinutes(10));
		HelpRequest help = helpRequestRepository.saveAndFlush(HelpRequest.send(link.getId(), recipient.getId(),
				guardian.getId(), now().minusMinutes(3), LocationStatus.NOT_SHARED, null));

		home(link.getId(), guardianToken)
				.andExpect(jsonPath("$.data.recipient.status.type").value("HELP_REQUESTED"))
				.andExpect(jsonPath("$.data.recipient.status.messageId").value(help.getId().toString()))
				.andExpect(jsonPath("$.data.recipient.status.signalName").value("도움 요청"))
				.andExpect(jsonPath("$.data.recipient.status.elapsedMinutes").value(3));

		help.markOpened(now());
		helpRequestRepository.saveAndFlush(help);

		home(link.getId(), guardianToken)
				.andExpect(jsonPath("$.data.recipient.status.type").value("NO_RESPONSE"));
	}

	@Test
	void recentActivitiesAreTheLatestThreeRecordsNewestFirst() throws Exception {
		deliveredSignal("첫째", now().minusMinutes(40));
		Message second = deliveredSignal("둘째", now().minusMinutes(30));
		HelpRequest third = helpRequestRepository.saveAndFlush(HelpRequest.send(link.getId(), recipient.getId(),
				guardian.getId(), now().minusMinutes(20), LocationStatus.NOT_SHARED, null));
		Message fourth = deliveredSignal("넷째", now().minusMinutes(10));

		home(link.getId(), guardianToken)
				.andExpect(jsonPath("$.data.recentActivities", hasSize(3)))
				.andExpect(jsonPath("$.data.recentActivities[0].id").value(fourth.getId().toString()))
				.andExpect(jsonPath("$.data.recentActivities[0].kind").value("GUARDIAN_SIGNAL"))
				.andExpect(jsonPath("$.data.recentActivities[0].summaryStatus").value("WAITING"))
				.andExpect(jsonPath("$.data.recentActivities[1].id").value(third.getId().toString()))
				.andExpect(jsonPath("$.data.recentActivities[1].kind").value("HELP_REQUEST"))
				.andExpect(jsonPath("$.data.recentActivities[2].id").value(second.getId().toString()));
	}

	@Test
	void favoriteSignalsFollowTheChosenOrderWithSignalShape() throws Exception {
		Signal walk = saveSignal("가자", List.of(RhythmBeat.LONG, RhythmBeat.LONG));
		Signal meal = saveSignal("밥 먹자", List.of(RhythmBeat.SHORT, RhythmBeat.SHORT));
		favoriteSignalRepository.saveAndFlush(new FavoriteSignal(link.getId(), meal.getId(), 0));
		favoriteSignalRepository.saveAndFlush(new FavoriteSignal(link.getId(), walk.getId(), 1));

		home(link.getId(), guardianToken)
				.andExpect(jsonPath("$.data.favoriteSignals", hasSize(2)))
				.andExpect(jsonPath("$.data.favoriteSignals[0].id").value(meal.getId().toString()))
				.andExpect(jsonPath("$.data.favoriteSignals[0].name").value("밥 먹자"))
				.andExpect(jsonPath("$.data.favoriteSignals[0].rhythm", hasSize(2)))
				.andExpect(jsonPath("$.data.favoriteSignals[0].durationMs").isNumber())
				.andExpect(jsonPath("$.data.favoriteSignals[1].id").value(walk.getId().toString()));
	}

	// 조회 API라 해제된 연결도 정상 응답(명세 해석, Pending Decisions).
	@Test
	void endedLinkStillReturnsHome() throws Exception {
		link.end(now());
		linkRepository.saveAndFlush(link);

		home(link.getId(), guardianToken).andExpect(status().isOk());
	}

	@Test
	void recipientOrRolelessUserIsForbidden() throws Exception {
		home(link.getId(), tokenProvider.createAccessToken(recipient.getId(), "RECIPIENT"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
		home(link.getId(), tokenProvider.createAccessToken(UUID.randomUUID(), null))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_REQUIRED"));
	}

	@Test
	void nonMemberOrUnknownLinkIsNotFound() throws Exception {
		home(link.getId(), tokenProvider.createAccessToken(UUID.randomUUID(), "GUARDIAN"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
		home(UUID.randomUUID(), guardianToken)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void malformedLinkIdIsValidationFailed() throws Exception {
		mockMvc.perform(get("/api/v1/links/lnk_1/home").header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("linkId"));
	}

	// 탈퇴 등으로 당사자 정보가 없으면 404(명세 해석, Pending Decisions).
	@Test
	void missingRecipientUserIsNotFound() throws Exception {
		Link orphan = linkRepository.saveAndFlush(Link.connect(
				saveUser("home-g2-" + UUID.randomUUID() + "@example.com", "박보호", Role.GUARDIAN).getId(),
				UUID.randomUUID(), now()));
		String token = tokenProvider.createAccessToken(orphan.getGuardianId(), "GUARDIAN");

		home(orphan.getId(), token)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	private ResultActions home(UUID linkId, String token) throws Exception {
		return mockMvc.perform(get("/api/v1/links/" + linkId + "/home")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private User saveUser(String email, String name, Role role) {
		User user = User.register(email, "hash", name);
		user.selectRole(role, now());
		return userRepository.saveAndFlush(user);
	}

	// 기기 전달까지 된 보호자 신호 — 응답 상태는 WAITING에서 시작한다.
	private Message deliveredSignal(String name, LocalDateTime requestedAt) {
		Message message = messageRepository.save(Message.send(link.getId(), guardian.getId(), recipient.getId(),
				MessageKind.SIGNAL, new SignalSnapshot(UUID.randomUUID(), name, null, SignalCategory.DAILY, "pill",
						List.of("SHORT"), List.of(150), 150),
				requestedAt, Duration.ofSeconds(60)));
		message.ack(requestedAt.plusSeconds(2), requestedAt.plusSeconds(2), 30);
		return messageRepository.saveAndFlush(message);
	}

	private Signal saveSignal(String name, List<RhythmBeat> rhythm) {
		return signalRepository.saveAndFlush(Signal.createCustom(link.getId(), name, null,
				com.sonulim.domain.signal.domain.SignalCategory.DAILY, "icon", rhythm));
	}
}
