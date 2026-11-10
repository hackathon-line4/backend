package com.sonulim.domain.messaging.api;

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
import org.springframework.http.MediaType;
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
import com.sonulim.global.security.JwtTokenProvider;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MessageQueryApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private static final int WAIT_MINUTES = 10;

	private UUID linkId;
	private User guardian;
	private User recipient;
	private String guardianToken;
	private String recipientToken;

	@BeforeEach
	void setUp() {
		messageRepository.deleteAll();
		linkId = UUID.randomUUID();
		guardian = saveUser("guardian-" + UUID.randomUUID() + "@example.com", "이영희", Role.GUARDIAN);
		recipient = saveUser("recipient-" + UUID.randomUUID() + "@example.com", "김하늘", Role.RECIPIENT);
		guardianToken = jwtTokenProvider.createAccessToken(guardian.getId(), "GUARDIAN");
		recipientToken = jwtTokenProvider.createAccessToken(recipient.getId(), "RECIPIENT");
	}

	private User saveUser(String email, String name, Role role) {
		User user = User.register(email, "hash", name);
		user.selectRole(role, LocalDateTime.now());
		return userRepository.saveAndFlush(user);
	}

	private Message sent(MessageKind kind, SignalCategory category) {
		return messageRepository.save(Message.send(linkId, guardian.getId(), recipient.getId(), kind,
				new SignalSnapshot(UUID.randomUUID(), "약 먹을 시간", "저녁 약을 먹자는 뜻", category, "pill",
						List.of("SHORT", "SHORT", "LONG"), 1900),
				LocalDateTime.now().minusMinutes(1), Duration.ofSeconds(60)));
	}

	private ResultActions incoming(String token) throws Exception {
		return mockMvc.perform(get("/api/v1/messages/incoming")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	@Test
	void incomingReturnsSignalsThatShouldStillBeVibrating() throws Exception {
		sent(MessageKind.SIGNAL, SignalCategory.DAILY);

		incoming(recipientToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].guardianName").value("이영희"))
				.andExpect(jsonPath("$.data.items[0].signal.rhythm", hasSize(3)))
				.andExpect(jsonPath("$.data.items[0].signal.durationMs").value(1900))
				// 일반 신호는 앱 기본값을 쓰라는 뜻으로 null을 내려준다
				.andExpect(jsonPath("$.data.items[0].playbackPolicy", hasKey("repeatIntervalSec")))
				.andExpect(jsonPath("$.data.items[0].playbackPolicy.repeatIntervalSec").value(nullValue()))
				.andExpect(jsonPath("$.data.items[0].playbackPolicy.bypassDnd").value(false));
	}

	@Test
	void anEmergencySignalCarriesItsPlaybackPolicy() throws Exception {
		sent(MessageKind.SIGNAL, SignalCategory.EMERGENCY);

		incoming(recipientToken)
				.andExpect(jsonPath("$.data.items[0].isEmergency").value(true))
				.andExpect(jsonPath("$.data.items[0].playbackPolicy.repeatIntervalSec").value(10))
				.andExpect(jsonPath("$.data.items[0].playbackPolicy.maxDurationSec").value(60))
				.andExpect(jsonPath("$.data.items[0].playbackPolicy.bypassDnd").value(true));
	}

	@Test
	void aMessageThatWasAlreadyRespondedToIsNoLongerIncoming() throws Exception {
		Message message = sent(MessageKind.SIGNAL, SignalCategory.DAILY);
		LocalDateTime delivered = LocalDateTime.now();
		message.ack(delivered, delivered, WAIT_MINUTES);
		message.respond(delivered.plusSeconds(5));
		messageRepository.save(message);

		incoming(recipientToken).andExpect(jsonPath("$.data.items", hasSize(0)));
	}

	@Test
	void theGuardianCannotReadTheRecipientInbox() throws Exception {
		sent(MessageKind.SIGNAL, SignalCategory.DAILY);

		incoming(guardianToken)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void detailShowsBothPartiesAndTheTimeline() throws Exception {
		Message message = sent(MessageKind.SIGNAL, SignalCategory.DAILY);
		LocalDateTime delivered = LocalDateTime.now();
		message.ack(delivered, delivered, WAIT_MINUTES);
		message.reportPlayback(3, delivered.plusSeconds(6));
		message.markNoResponse(delivered.plusMinutes(WAIT_MINUTES));
		messageRepository.save(message);

		mockMvc.perform(get("/api/v1/messages/{id}", message.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.message.sender.name").value("이영희"))
				.andExpect(jsonPath("$.data.message.receiver.name").value("김하늘"))
				.andExpect(jsonPath("$.data.message.response.status").value("NO_RESPONSE"))
				.andExpect(jsonPath("$.data.timeline", hasSize(4)))
				.andExpect(jsonPath("$.data.timeline[0].step").value("SENT"))
				.andExpect(jsonPath("$.data.timeline[2].step").value("PLAYED"))
				.andExpect(jsonPath("$.data.timeline[2].count").value(3))
				// 응답 없음이면 더 기다릴 수 있다
				.andExpect(jsonPath("$.data.canExtendWait").value(true))
				.andExpect(jsonPath("$.data", hasKey("relatedHelpRequest")))
				.andExpect(jsonPath("$.data.relatedHelpRequest").value(nullValue()));
	}

	@Test
	void anotherGuardianGets404OnDetail() throws Exception {
		Message message = sent(MessageKind.SIGNAL, SignalCategory.DAILY);
		String stranger = jwtTokenProvider.createAccessToken(UUID.randomUUID(), "GUARDIAN");

		mockMvc.perform(get("/api/v1/messages/{id}", message.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void extendWaitBringsANoResponseRecordBackToWaiting() throws Exception {
		Message message = sent(MessageKind.SIGNAL, SignalCategory.DAILY);
		LocalDateTime delivered = LocalDateTime.now().minusMinutes(20);
		message.ack(delivered, delivered, WAIT_MINUTES);
		message.markNoResponse(delivered.plusMinutes(WAIT_MINUTES));
		messageRepository.save(message);

		mockMvc.perform(post("/api/v1/messages/{id}/extend-wait", message.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"minutes\":10}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.response.status").value("WAITING"))
				// 판정이 있었다는 기록은 지우지 않는다
				.andExpect(jsonPath("$.data.response.noResponseAt").isNotEmpty());
	}

	@Test
	void extendWaitWithoutMinutesUsesTheServerDefault() throws Exception {
		Message message = sent(MessageKind.SIGNAL, SignalCategory.DAILY);
		LocalDateTime delivered = LocalDateTime.now();
		message.ack(delivered, delivered, WAIT_MINUTES);
		messageRepository.save(message);

		mockMvc.perform(post("/api/v1/messages/{id}/extend-wait", message.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken)
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.response.waitUntil").isNotEmpty());
	}

	@Test
	void extendWaitIsRejectedOnceTheTouchArrived() throws Exception {
		Message message = sent(MessageKind.SIGNAL, SignalCategory.DAILY);
		LocalDateTime delivered = LocalDateTime.now();
		message.ack(delivered, delivered, WAIT_MINUTES);
		message.respond(delivered.plusSeconds(5));
		messageRepository.save(message);

		mockMvc.perform(post("/api/v1/messages/{id}/extend-wait", message.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken)
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MESSAGE_NOT_WAITING"))
				.andExpect(jsonPath("$.error.details.responseStatus").value("RESPONDED"));
	}

	@Test
	void aMinutesValueOutsideTheAllowedRangeIsRejected() throws Exception {
		Message message = sent(MessageKind.SIGNAL, SignalCategory.DAILY);

		mockMvc.perform(post("/api/v1/messages/{id}/extend-wait", message.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"minutes\":120}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("minutes"));
	}
}
