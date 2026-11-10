package com.sonulim.domain.recipient.api;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.account.domain.UserRepository;
import com.sonulim.domain.messaging.domain.Message;
import com.sonulim.domain.messaging.domain.MessageKind;
import com.sonulim.domain.messaging.domain.MessageRepository;
import com.sonulim.domain.messaging.domain.SignalCategory;
import com.sonulim.domain.messaging.domain.SignalSnapshot;
import com.sonulim.domain.recipient.domain.CapturedLocation;
import com.sonulim.domain.recipient.domain.HelpRequest;
import com.sonulim.domain.recipient.domain.HelpRequestRepository;
import com.sonulim.domain.recipient.domain.LocationStatus;
import com.sonulim.global.security.JwtTokenProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HelpRequestDetailApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private HelpRequestRepository helpRequestRepository;

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private UUID linkId;
	private User guardian;
	private User recipient;
	private String guardianToken;

	@BeforeEach
	void setUp() {
		helpRequestRepository.deleteAll();
		messageRepository.deleteAll();
		linkId = UUID.randomUUID();
		guardian = saveUser("g-" + UUID.randomUUID() + "@example.com", "이영희", Role.GUARDIAN);
		recipient = saveUser("r-" + UUID.randomUUID() + "@example.com", "김하늘", Role.RECIPIENT);
		guardianToken = jwtTokenProvider.createAccessToken(guardian.getId(), "GUARDIAN");
	}

	private User saveUser(String email, String name, Role role) {
		User user = User.register(email, "hash", name);
		user.selectRole(role, LocalDateTime.now());
		return userRepository.saveAndFlush(user);
	}

	private HelpRequest helpRequestAt(LocalDateTime sentAt, LocationStatus status, CapturedLocation location) {
		return helpRequestRepository.save(HelpRequest.send(linkId, recipient.getId(), guardian.getId(),
				sentAt, status, location));
	}

	private Message signalAt(String name, LocalDateTime sentAt) {
		return messageRepository.save(Message.send(linkId, guardian.getId(), recipient.getId(), MessageKind.SIGNAL,
				new SignalSnapshot(UUID.randomUUID(), name, null, SignalCategory.DAILY, "pill",
						List.of("SHORT"), 500),
				sentAt, Duration.ofSeconds(60)));
	}

	private ResultActions read(UUID id, String token) throws Exception {
		return mockMvc.perform(get("/api/v1/help-requests/{id}", id)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	@Test
	void detailShowsTheSenderAndTheAttachedLocation() throws Exception {
		LocalDateTime sentAt = LocalDateTime.now().minusMinutes(10);
		HelpRequest request = helpRequestAt(sentAt, LocationStatus.ATTACHED,
				new CapturedLocation(new BigDecimal("37.566500"), new BigDecimal("126.978000"), 25, sentAt));

		read(request.getId(), guardianToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.helpRequest.sender.name").value("김하늘"))
				.andExpect(jsonPath("$.data.helpRequest.locationStatus").value("ATTACHED"))
				.andExpect(jsonPath("$.data.helpRequest.location.latitude").value(37.5665))
				// 주소 변환 전이라 비어 있지만 필드는 유지한다
				.andExpect(jsonPath("$.data.helpRequest.location", hasKey("address")))
				.andExpect(jsonPath("$.data.helpRequest.location.address").value(nullValue()));
	}

	@Test
	void aRequestWithoutLocationKeepsTheFieldAsNull() throws Exception {
		HelpRequest request = helpRequestAt(LocalDateTime.now().minusMinutes(5), LocationStatus.PENDING, null);

		read(request.getId(), guardianToken)
				.andExpect(jsonPath("$.data.helpRequest.locationStatus").value("PENDING"))
				.andExpect(jsonPath("$.data.helpRequest", hasKey("location")))
				.andExpect(jsonPath("$.data.helpRequest.location").value(nullValue()));
	}

	@Test
	void followingMessagesStopAtTheNextHelpRequest() throws Exception {
		LocalDateTime first = LocalDateTime.now().minusHours(2);
		HelpRequest request = helpRequestAt(first, LocationStatus.PENDING, null);
		signalAt("갈게", first.plusMinutes(1));
		signalAt("기다려", first.plusMinutes(2));
		// 다음 도움 요청 이후의 신호는 그 요청에 딸린 대화다
		helpRequestAt(first.plusMinutes(30), LocationStatus.PENDING, null);
		signalAt("다음 요청 뒤 신호", first.plusMinutes(31));

		read(request.getId(), guardianToken)
				.andExpect(jsonPath("$.data.followingMessages", hasSize(2)))
				.andExpect(jsonPath("$.data.followingMessages[0].signalName").value("갈게"))
				.andExpect(jsonPath("$.data.followingMessages[1].signalName").value("기다려"));
	}

	@Test
	void signalsSentBeforeTheRequestAreNotIncluded() throws Exception {
		LocalDateTime sentAt = LocalDateTime.now().minusHours(1);
		signalAt("요청 전 신호", sentAt.minusMinutes(5));
		HelpRequest request = helpRequestAt(sentAt, LocationStatus.PENDING, null);

		read(request.getId(), guardianToken)
				.andExpect(jsonPath("$.data.followingMessages", hasSize(0)));
	}

	@Test
	void readingTheDetailDoesNotRecordThatItWasOpened() throws Exception {
		HelpRequest request = helpRequestAt(LocalDateTime.now().minusMinutes(5), LocationStatus.PENDING, null);

		read(request.getId(), guardianToken)
				.andExpect(jsonPath("$.data.helpRequest.guardianOpenedAt").value(nullValue()));

		// 열람은 별도 API가 기록한다 — 상세를 다시 열어도 '열어 본 시각'이 밀리지 않는다
		assertThat(helpRequestRepository.findById(request.getId()).orElseThrow().getGuardianOpenedAt()).isNull();
	}

	@Test
	void theRecipientCannotReadTheDetail() throws Exception {
		HelpRequest request = helpRequestAt(LocalDateTime.now(), LocationStatus.PENDING, null);

		read(request.getId(), jwtTokenProvider.createAccessToken(recipient.getId(), "RECIPIENT"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void anotherGuardianGets404() throws Exception {
		HelpRequest request = helpRequestAt(LocalDateTime.now(), LocationStatus.PENDING, null);

		read(request.getId(), jwtTokenProvider.createAccessToken(UUID.randomUUID(), "GUARDIAN"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}
}
