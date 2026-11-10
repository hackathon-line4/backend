package com.sonulim.domain.messaging.api;

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
import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkRepository;
import com.sonulim.domain.messaging.domain.DeliveryStatus;
import com.sonulim.domain.messaging.domain.MessageRepository;
import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.global.push.LoggingPushSender;
import com.sonulim.global.push.PushType;
import com.sonulim.global.security.JwtTokenProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SendMessageApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private LinkRepository linkRepository;

	@Autowired
	private SignalRepository signalRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private LoggingPushSender pushSender;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private Link link;
	private User guardian;
	private User recipient;
	private Signal signal;
	private String guardianToken;

	@BeforeEach
	void setUp() {
		messageRepository.deleteAll();
		signalRepository.deleteAll();
		linkRepository.deleteAll();
		pushSender.clear();
		guardian = saveUser("g-" + UUID.randomUUID() + "@example.com", "이영희", Role.GUARDIAN);
		recipient = saveUser("r-" + UUID.randomUUID() + "@example.com", "김하늘", Role.RECIPIENT);
		link = linkRepository.save(Link.connect(guardian.getId(), recipient.getId(), LocalDateTime.now()));
		signal = signalRepository.save(Signal.createCustom(link.getId(), "약 먹을 시간", "저녁 약을 먹자는 뜻",
				SignalCategory.DAILY, "pill", List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG)));
		guardianToken = jwtTokenProvider.createAccessToken(guardian.getId(), "GUARDIAN");
	}

	private User saveUser(String email, String name, Role role) {
		User user = User.register(email, "hash", name);
		user.selectRole(role, LocalDateTime.now());
		return userRepository.saveAndFlush(user);
	}

	private ResultActions send(String token, UUID linkId, UUID signalId, String key) throws Exception {
		return mockMvc.perform(post("/api/v1/links/{linkId}/messages", linkId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"signalId\":\"" + signalId + "\"}"));
	}

	private ResultActions send() throws Exception {
		return send(guardianToken, link.getId(), signal.getId(), UUID.randomUUID().toString());
	}

	@Test
	void sendingCreatesTheRecordAndWaitsForTheDeviceToConfirm() throws Exception {
		send()
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.sender.name").value("이영희"))
				.andExpect(jsonPath("$.data.receiver.name").value("김하늘"))
				.andExpect(jsonPath("$.data.signal.name").value("약 먹을 시간"))
				.andExpect(jsonPath("$.data.signal.pulses").isNotEmpty())
				// 푸시를 보냈다고 전달 완료가 되지는 않는다 — ack를 기다린다
				.andExpect(jsonPath("$.data.delivery.status").value("REQUESTED"))
				.andExpect(jsonPath("$.data.delivery.deliveredAt").isEmpty())
				.andExpect(jsonPath("$.data.response.status").isEmpty());
	}

	@Test
	void theRecipientGetsAPushWithTheMessageToOpen() throws Exception {
		send().andExpect(status().isCreated());

		assertThat(pushSender.sent()).singleElement().satisfies(push -> {
			assertThat(push.type()).isEqualTo(PushType.SIGNAL);
			assertThat(push.recipientUserId()).isEqualTo(recipient.getId());
			assertThat(push.data()).containsKey("messageId");
		});
	}

	@Test
	void anEmergencySignalIsMarkedSo() throws Exception {
		Signal urgent = signalRepository.save(Signal.createCustom(link.getId(), "위험해", null,
				SignalCategory.EMERGENCY, "siren", List.of(RhythmBeat.LONG, RhythmBeat.LONG, RhythmBeat.LONG)));

		send(guardianToken, link.getId(), urgent.getId(), UUID.randomUUID().toString())
				.andExpect(jsonPath("$.data.isEmergency").value(true));
	}

	@Test
	void retryingWithTheSameKeyDoesNotBuzzTheRecipientTwice() throws Exception {
		String key = UUID.randomUUID().toString();

		String first = send(guardianToken, link.getId(), signal.getId(), key)
				.andReturn().getResponse().getContentAsString();
		String retried = send(guardianToken, link.getId(), signal.getId(), key)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		assertThat(retried).isEqualTo(first);
		assertThat(messageRepository.count()).isEqualTo(1);
		assertThat(pushSender.sent()).hasSize(1);
	}

	@Test
	void sendingWithoutAnIdempotencyKeyIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/links/{linkId}/messages", link.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"signalId\":\"" + signal.getId() + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
	}

	@Test
	void theRecipientCannotSend() throws Exception {
		send(jwtTokenProvider.createAccessToken(recipient.getId(), "RECIPIENT"), link.getId(), signal.getId(),
				UUID.randomUUID().toString())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void anotherGuardianGets404SoTheLinkStaysHidden() throws Exception {
		send(jwtTokenProvider.createAccessToken(UUID.randomUUID(), "GUARDIAN"), link.getId(), signal.getId(),
				UUID.randomUUID().toString())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void resendingMakesANewRecordAndLeavesTheOriginalAlone() throws Exception {
		String body = send().andReturn().getResponse().getContentAsString();
		String originalId = com.jayway.jsonpath.JsonPath.read(body, "$.data.id");

		mockMvc.perform(post("/api/v1/messages/{id}/resend", originalId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken)
						.header("Idempotency-Key", UUID.randomUUID().toString()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.resendOf").value(originalId))
				.andExpect(jsonPath("$.data.delivery.status").value("REQUESTED"));

		assertThat(messageRepository.count()).isEqualTo(2);
		assertThat(messageRepository.findById(UUID.fromString(originalId)).orElseThrow().getResendOfId()).isNull();
	}

	@Test
	void resendingUsesTheSignalsCurrentValue() throws Exception {
		String body = send().andReturn().getResponse().getContentAsString();
		String originalId = com.jayway.jsonpath.JsonPath.read(body, "$.data.id");
		signal.updateName("이름이 바뀐 신호");
		signalRepository.saveAndFlush(signal);

		mockMvc.perform(post("/api/v1/messages/{id}/resend", originalId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken)
						.header("Idempotency-Key", UUID.randomUUID().toString()))
				.andExpect(jsonPath("$.data.signal.name").value("이름이 바뀐 신호"));

		// 원래 기록의 스냅샷은 보낸 시점 그대로다
		assertThat(messageRepository.findById(UUID.fromString(originalId)).orElseThrow()
				.getSignalSnapshot().name()).isEqualTo("약 먹을 시간");
	}

	@Test
	void aDeletedSignalCannotBeResent() throws Exception {
		String body = send().andReturn().getResponse().getContentAsString();
		String originalId = com.jayway.jsonpath.JsonPath.read(body, "$.data.id");
		signalRepository.deleteAll();

		mockMvc.perform(post("/api/v1/messages/{id}/resend", originalId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken)
						.header("Idempotency-Key", UUID.randomUUID().toString()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("SIGNAL_DELETED"));
	}
}
