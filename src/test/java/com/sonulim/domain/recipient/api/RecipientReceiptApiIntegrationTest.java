package com.sonulim.domain.recipient.api;

import java.time.LocalDateTime;
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

import com.sonulim.domain.recipient.domain.HelpRequest;
import com.sonulim.domain.recipient.domain.HelpRequestRepository;
import com.sonulim.domain.recipient.domain.LocationStatus;
import com.sonulim.domain.recipient.domain.RecipientSignal;
import com.sonulim.domain.recipient.domain.RecipientSignalRepository;
import com.sonulim.domain.recipient.domain.RecipientSignalType;
import com.sonulim.global.push.LoggingPushSender;
import com.sonulim.global.push.PushType;
import com.sonulim.global.security.JwtTokenProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecipientReceiptApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RecipientSignalRepository recipientSignalRepository;

	@Autowired
	private HelpRequestRepository helpRequestRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private LoggingPushSender pushSender;

	private UUID guardianId;
	private UUID recipientId;
	private String guardianToken;

	@BeforeEach
	void setUp() {
		recipientSignalRepository.deleteAll();
		helpRequestRepository.deleteAll();
		pushSender.clear();
		guardianId = UUID.randomUUID();
		recipientId = UUID.randomUUID();
		guardianToken = jwtTokenProvider.createAccessToken(guardianId, "GUARDIAN");
	}

	private RecipientSignal givenCall() {
		return recipientSignalRepository.save(RecipientSignal.send(UUID.randomUUID(), recipientId, guardianId,
				RecipientSignalType.CALL_GUARDIAN, LocalDateTime.now()));
	}

	private HelpRequest givenHelpRequest() {
		return helpRequestRepository.save(HelpRequest.send(UUID.randomUUID(), recipientId, guardianId,
				LocalDateTime.now(), LocationStatus.PENDING, null));
	}

	private ResultActions report(String path, UUID id, String token, String type) throws Exception {
		return mockMvc.perform(post("/api/v1/{path}/{id}/receipts", path, id)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"" + type + "\"}"));
	}

	@Test
	void deliveryOfACallIsRecorded() throws Exception {
		RecipientSignal call = givenCall();

		report("recipient-signals", call.getId(), guardianToken, "DELIVERED")
				.andExpect(status().isNoContent());

		RecipientSignal stored = recipientSignalRepository.findById(call.getId()).orElseThrow();
		assertThat(stored.getGuardianDeliveredAt()).isNotNull();
		assertThat(stored.getGuardianOpenedAt()).isNull();
	}

	@Test
	void openingACallWithoutADeliveryReportFillsBoth() throws Exception {
		RecipientSignal call = givenCall();

		report("recipient-signals", call.getId(), guardianToken, "OPENED")
				.andExpect(status().isNoContent());

		RecipientSignal stored = recipientSignalRepository.findById(call.getId()).orElseThrow();
		assertThat(stored.getGuardianOpenedAt()).isNotNull();
		assertThat(stored.getGuardianDeliveredAt()).isEqualTo(stored.getGuardianOpenedAt());
	}

	@Test
	void repeatedReportsKeepTheFirstTime() throws Exception {
		HelpRequest request = givenHelpRequest();

		report("help-requests", request.getId(), guardianToken, "OPENED").andExpect(status().isNoContent());
		LocalDateTime first = helpRequestRepository.findById(request.getId()).orElseThrow().getGuardianOpenedAt();
		report("help-requests", request.getId(), guardianToken, "OPENED").andExpect(status().isNoContent());

		assertThat(helpRequestRepository.findById(request.getId()).orElseThrow().getGuardianOpenedAt())
				.isEqualTo(first);
	}

	@Test
	void openingAHelpRequestTellsTheRecipientItWasSeen() throws Exception {
		HelpRequest request = givenHelpRequest();

		report("help-requests", request.getId(), guardianToken, "OPENED").andExpect(status().isNoContent());

		assertThat(pushSender.sent()).singleElement().satisfies(push -> {
			assertThat(push.type()).isEqualTo(PushType.HELP_CONFIRMED);
			// '확인함' 진동은 요청을 보낸 당사자에게 간다
			assertThat(push.recipientUserId()).isEqualTo(recipientId);
			assertThat(push.data()).containsEntry("helpRequestId", request.getId().toString());
		});
	}

	@Test
	void openingItAgainDoesNotBuzzTheRecipientTwice() throws Exception {
		HelpRequest request = givenHelpRequest();

		report("help-requests", request.getId(), guardianToken, "OPENED").andExpect(status().isNoContent());
		report("help-requests", request.getId(), guardianToken, "OPENED").andExpect(status().isNoContent());

		assertThat(pushSender.sent()).hasSize(1);
	}

	@Test
	void arrivalAloneDoesNotTellTheRecipientAnything() throws Exception {
		HelpRequest request = givenHelpRequest();

		report("help-requests", request.getId(), guardianToken, "DELIVERED").andExpect(status().isNoContent());

		assertThat(pushSender.sent()).isEmpty();
	}

	@Test
	void anUnknownReceiptTypeIsRejected() throws Exception {
		HelpRequest request = givenHelpRequest();

		report("help-requests", request.getId(), guardianToken, "READ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("type"));
	}

	@Test
	void aBrokenBodyIsRejectedInsteadOfCrashing() throws Exception {
		mockMvc.perform(post("/api/v1/help-requests/{id}/receipts", givenHelpRequest().getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"type\":"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
	}

	@Test
	void anEmptyBodyIsRejectedInsteadOfCrashing() throws Exception {
		mockMvc.perform(post("/api/v1/help-requests/{id}/receipts", givenHelpRequest().getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken)
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
	}

	@Test
	void aPathThatIsNotAUuidIsRejectedInsteadOfCrashing() throws Exception {
		mockMvc.perform(post("/api/v1/help-requests/{id}/receipts", "not-a-uuid")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"OPENED\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("id"));
	}

	@Test
	void theRecipientCannotReportOnTheirOwnRequest() throws Exception {
		HelpRequest request = givenHelpRequest();
		String recipientToken = jwtTokenProvider.createAccessToken(recipientId, "RECIPIENT");

		report("help-requests", request.getId(), recipientToken, "OPENED")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void anotherGuardianGets404SoTheRequestStaysHidden() throws Exception {
		HelpRequest request = givenHelpRequest();
		String strangerToken = jwtTokenProvider.createAccessToken(UUID.randomUUID(), "GUARDIAN");

		report("help-requests", request.getId(), strangerToken, "OPENED")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void withoutATokenItIs401() throws Exception {
		HelpRequest request = givenHelpRequest();

		mockMvc.perform(post("/api/v1/help-requests/{id}/receipts", request.getId())
						.contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"OPENED\"}"))
				.andExpect(status().isUnauthorized());
	}
}
