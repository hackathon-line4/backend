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

import com.sonulim.domain.messaging.domain.DeliveryStatus;
import com.sonulim.domain.messaging.domain.Message;
import com.sonulim.domain.messaging.domain.MessageKind;
import com.sonulim.domain.messaging.domain.MessageRepository;
import com.sonulim.domain.messaging.domain.ResponseStatus;
import com.sonulim.domain.messaging.domain.SignalCategory;
import com.sonulim.domain.messaging.domain.SignalSnapshot;
import com.sonulim.global.security.JwtTokenProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MessageReceiptApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private UUID guardianId;
	private UUID recipientId;
	private String recipientToken;

	@BeforeEach
	void setUp() {
		messageRepository.deleteAll();
		guardianId = UUID.randomUUID();
		recipientId = UUID.randomUUID();
		recipientToken = jwtTokenProvider.createAccessToken(recipientId, "RECIPIENT");
	}

	private Message givenSentMessage(MessageKind kind) {
		return messageRepository.save(Message.send(UUID.randomUUID(), guardianId, recipientId, kind,
				new SignalSnapshot(UUID.randomUUID(), "약 먹을 시간", "저녁 약을 먹자는 뜻", SignalCategory.DAILY,
						"pill", List.of("SHORT", "SHORT", "LONG"), List.of(150, 150, 150, 150, 1000), 1900),
				LocalDateTime.now(), Duration.ofSeconds(60)));
	}

	private ResultActions ack(String token, UUID messageId, LocalDateTime receivedAt) throws Exception {
		return mockMvc.perform(post("/api/v1/messages/{id}/ack", messageId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"receivedAt\":\"" + receivedAt + "\"}"));
	}

	private ResultActions touch(String token, UUID messageId) throws Exception {
		return mockMvc.perform(post("/api/v1/messages/{id}/response", messageId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"touchedAt\":\"" + LocalDateTime.now() + "\"}"));
	}

	@Test
	void ackReturnsDeliveryTimeAndHowLongToKeepVibrating() throws Exception {
		Message message = givenSentMessage(MessageKind.SIGNAL);

		ack(recipientToken, message.getId(), LocalDateTime.now())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(true))
				.andExpect(jsonPath("$.data.deliveredAt").isNotEmpty())
				.andExpect(jsonPath("$.data.waitUntil").isNotEmpty());

		assertThat(messageRepository.findById(message.getId()).orElseThrow().getDeliveryStatus())
				.isEqualTo(DeliveryStatus.DELIVERED);
	}

	@Test
	void ackForAPracticeSignalKeepsWaitUntilPresentButNull() throws Exception {
		Message practice = givenSentMessage(MessageKind.PRACTICE);

		ack(recipientToken, practice.getId(), LocalDateTime.now())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.deliveredAt").isNotEmpty())
				// 공통 규칙: 값이 없으면 필드를 빼지 않고 null로 둔다. doesNotExist()는 둘 다 통과하므로 키 존재를 따로 본다
				.andExpect(jsonPath("$.data", hasKey("waitUntil")))
				.andExpect(jsonPath("$.data.waitUntil").value(nullValue()));
	}

	@Test
	void repeatedAckReturnsTheSameDeliveryTime() throws Exception {
		Message message = givenSentMessage(MessageKind.SIGNAL);

		String first = ack(recipientToken, message.getId(), LocalDateTime.now())
				.andReturn().getResponse().getContentAsString();
		String second = ack(recipientToken, message.getId(), LocalDateTime.now().plusSeconds(30))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(second).isEqualTo(first);
	}

	@Test
	void playbackReportReturns204AndKeepsTheHighestCount() throws Exception {
		Message message = givenSentMessage(MessageKind.SIGNAL);

		report(message.getId(), 3).andExpect(status().isNoContent());
		report(message.getId(), 1).andExpect(status().isNoContent()); // 뒤바뀌어 도착한 보고

		assertThat(messageRepository.findById(message.getId()).orElseThrow().getPlayCount()).isEqualTo(3);
	}

	@Test
	void playbackCountBelowOneIsRejected() throws Exception {
		Message message = givenSentMessage(MessageKind.SIGNAL);

		report(message.getId(), 0)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("count"));
	}

	@Test
	void touchWithoutAckRecordsBothAtOnce() throws Exception {
		Message message = givenSentMessage(MessageKind.SIGNAL);

		touch(recipientToken, message.getId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("RESPONDED"))
				.andExpect(jsonPath("$.data.respondedAt").isNotEmpty())
				.andExpect(jsonPath("$.data.late").value(false));

		Message responded = messageRepository.findById(message.getId()).orElseThrow();
		assertThat(responded.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
		assertThat(responded.getResponseStatus()).isEqualTo(ResponseStatus.RESPONDED);
	}

	@Test
	void touchingAPracticeSignalIsRejected() throws Exception {
		Message practice = givenSentMessage(MessageKind.PRACTICE);

		touch(recipientToken, practice.getId())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MESSAGE_NOT_RESPONDABLE"));
	}

	@Test
	void guardianCannotUseRecipientEndpoints() throws Exception {
		Message message = givenSentMessage(MessageKind.SIGNAL);
		String guardianToken = jwtTokenProvider.createAccessToken(guardianId, "GUARDIAN");

		ack(guardianToken, message.getId(), LocalDateTime.now())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void userWithoutARoleIsToldToPickOneFirst() throws Exception {
		Message message = givenSentMessage(MessageKind.SIGNAL);
		String noRoleToken = jwtTokenProvider.createAccessToken(UUID.randomUUID(), null);

		ack(noRoleToken, message.getId(), LocalDateTime.now())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_REQUIRED"));
	}

	@Test
	void anotherRecipientGets404SoTheRecordStaysHidden() throws Exception {
		Message message = givenSentMessage(MessageKind.SIGNAL);
		String strangerToken = jwtTokenProvider.createAccessToken(UUID.randomUUID(), "RECIPIENT");

		// 존재하지만 내 기록이 아니다 — 403이 아니라 404로 존재 자체를 숨긴다
		ack(strangerToken, message.getId(), LocalDateTime.now())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void withoutATokenItIs401() throws Exception {
		Message message = givenSentMessage(MessageKind.SIGNAL);

		mockMvc.perform(post("/api/v1/messages/{id}/ack", message.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"receivedAt\":\"" + LocalDateTime.now() + "\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void missingRequiredFieldIsRejected() throws Exception {
		Message message = givenSentMessage(MessageKind.SIGNAL);

		mockMvc.perform(post("/api/v1/messages/{id}/ack", message.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + recipientToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("receivedAt"));
	}

	private ResultActions report(UUID messageId, int count) throws Exception {
		return mockMvc.perform(post("/api/v1/messages/{id}/playback", messageId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + recipientToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"count\":" + count + "}"));
	}
}
