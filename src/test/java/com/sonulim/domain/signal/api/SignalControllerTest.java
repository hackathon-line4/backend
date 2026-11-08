package com.sonulim.domain.signal.api;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonulim.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SignalControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private SignalRepository signalRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private String bearerTokenFor(UUID userId) {
		return bearerTokenFor(userId, "GUARDIAN");
	}

	private String bearerTokenFor(UUID userId, String role) {
		return "Bearer " + jwtTokenProvider.createAccessToken(userId, role);
	}

	@Test
	void listReturnsEmptyArrayWhenTheLinkHasNoSignals() throws Exception {
		UUID linkId = UUID.randomUUID();

		mockMvc.perform(get("/api/v1/links/{linkId}/signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(true))
				.andExpect(jsonPath("$.data.items").isArray())
				.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@Test
	void listReturnsSignalsBelongingToThatLinkWithCorrectShape() throws Exception {
		UUID linkId = UUID.randomUUID();
		signalRepository.saveAndFlush(Signal.createCustom(linkId, "약 먹을 시간", "저녁 약을 먹자는 뜻",
				SignalCategory.DAILY, "pill", List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG)));

		mockMvc.perform(get("/api/v1/links/{linkId}/signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].name").value("약 먹을 시간"))
				.andExpect(jsonPath("$.data.items[0].source").value("CUSTOM"))
				.andExpect(jsonPath("$.data.items[0].category").value("DAILY"))
				.andExpect(jsonPath("$.data.items[0].rhythm[0]").value("SHORT"))
				.andExpect(jsonPath("$.data.items[0].durationMs").value(1900))
				.andExpect(jsonPath("$.data.items[0].practiced").value(false));
	}

	@Test
	void listRejectsARequesterWhoIsNotAGuardianWith403() throws Exception {
		mockMvc.perform(get("/api/v1/links/{linkId}/signals", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "RECIPIENT")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void detailReturnsTheSignalWhenItBelongsToThePathLink() throws Exception {
		UUID linkId = UUID.randomUUID();
		Signal signal = signalRepository.saveAndFlush(Signal.createCustom(linkId, "신호", null,
				SignalCategory.CHECK, "icon", List.of(RhythmBeat.SHORT, RhythmBeat.LONG)));

		mockMvc.perform(get("/api/v1/links/{linkId}/signals/{signalId}", linkId, signal.getId())
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(signal.getId().toString()))
				.andExpect(jsonPath("$.data.name").value("신호"));
	}

	@Test
	void detailReturns404WhenSignalDoesNotExist() throws Exception {
		mockMvc.perform(get("/api/v1/links/{linkId}/signals/{signalId}", UUID.randomUUID(), UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void detailReturns404WhenPathLinkIdDoesNotMatchTheSignalsRealLink() throws Exception {
		UUID actualLinkId = UUID.randomUUID();
		Signal signal = signalRepository.saveAndFlush(Signal.createCustom(actualLinkId, "신호", null,
				SignalCategory.CHECK, "icon", List.of(RhythmBeat.SHORT, RhythmBeat.LONG)));

		mockMvc.perform(get("/api/v1/links/{linkId}/signals/{signalId}", UUID.randomUUID(), signal.getId())
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void detailRejectsARequesterWhoIsNotAGuardianWith403() throws Exception {
		mockMvc.perform(get("/api/v1/links/{linkId}/signals/{signalId}", UUID.randomUUID(), UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "RECIPIENT")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/links/{linkId}/signals", UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void fullLifecycleCreateReadUpdateReadDeleteRead() throws Exception {
		UUID linkId = UUID.randomUUID();
		String token = bearerTokenFor(UUID.randomUUID());
		String createBody = objectMapper.writeValueAsString(
				new CreateSignalRequest("약 먹을 시간", "저녁 약", SignalCategory.DAILY, "pill",
						List.of("SHORT", "SHORT", "LONG")));

		// 1) 생성
		String createResponse = mockMvc.perform(post("/api/v1/links/{linkId}/signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.name").value("약 먹을 시간"))
				.andReturn().getResponse().getContentAsString();
		UUID signalId = UUID.fromString(objectMapper.readTree(createResponse).at("/data/id").asText());

		// 2) 조회 — 생성한 값 그대로
		mockMvc.perform(get("/api/v1/links/{linkId}/signals/{signalId}", linkId, signalId)
						.header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("약 먹을 시간"))
				.andExpect(jsonPath("$.data.rhythm[2]").value("LONG"));

		// 3) 수정 — 이름과 리듬만 변경
		String updateBody = objectMapper.writeValueAsString(
				new UpdateSignalRequest("약 먹을 시간(수정)", null, null, null, List.of("LONG", "LONG")));
		mockMvc.perform(patch("/api/v1/links/{linkId}/signals/{signalId}", linkId, signalId)
						.header(HttpHeaders.AUTHORIZATION, token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("약 먹을 시간(수정)"))
				.andExpect(jsonPath("$.data.rhythm[0]").value("LONG"))
				.andExpect(jsonPath("$.data.description").value("저녁 약"));

		// 4) 조회 — 수정된 값 반영
		mockMvc.perform(get("/api/v1/links/{linkId}/signals/{signalId}", linkId, signalId)
						.header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("약 먹을 시간(수정)"));

		// 5) 삭제
		mockMvc.perform(delete("/api/v1/links/{linkId}/signals/{signalId}", linkId, signalId)
						.header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isNoContent());

		// 6) 조회 — 삭제 후 404, 목록에서도 제외
		mockMvc.perform(get("/api/v1/links/{linkId}/signals/{signalId}", linkId, signalId)
						.header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/links/{linkId}/signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@Test
	void createRejectsAnInvalidRhythm() throws Exception {
		UUID linkId = UUID.randomUUID();
		String body = objectMapper.writeValueAsString(
				new CreateSignalRequest("이름", null, SignalCategory.CHECK, "icon", List.of("SHORT")));

		mockMvc.perform(post("/api/v1/links/{linkId}/signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("RHYTHM_INVALID"));
	}

	@Test
	void createRejectsADuplicateRhythmInTheSameLink() throws Exception {
		UUID linkId = UUID.randomUUID();
		signalRepository.saveAndFlush(Signal.createCustom(linkId, "기존", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG)));
		String body = objectMapper.writeValueAsString(
				new CreateSignalRequest("새 신호", null, SignalCategory.CHECK, "icon", List.of("SHORT", "LONG")));

		mockMvc.perform(post("/api/v1/links/{linkId}/signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("RHYTHM_DUPLICATE"));
	}

	@Test
	void createRejectsABlankName() throws Exception {
		UUID linkId = UUID.randomUUID();
		String body = objectMapper.writeValueAsString(
				new CreateSignalRequest("", null, SignalCategory.CHECK, "icon", List.of("SHORT", "LONG")));

		mockMvc.perform(post("/api/v1/links/{linkId}/signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
	}

	@Test
	void createRejectsARequesterWhoIsNotAGuardianWith403() throws Exception {
		UUID linkId = UUID.randomUUID();
		String body = objectMapper.writeValueAsString(
				new CreateSignalRequest("이름", null, SignalCategory.CHECK, "icon", List.of("SHORT", "LONG")));

		mockMvc.perform(post("/api/v1/links/{linkId}/signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "RECIPIENT"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void updateRejectsNameOrRhythmChangeOnATemplateSignal() throws Exception {
		UUID linkId = UUID.randomUUID();
		Signal templateSignal = signalRepository.saveAndFlush(Signal.fromTemplate(linkId, UUID.randomUUID(),
				"기본 신호", null, SignalCategory.CHECK, "icon", List.of(RhythmBeat.SHORT, RhythmBeat.LONG)));
		String body = objectMapper.writeValueAsString(
				new UpdateSignalRequest("바꾼 이름", null, null, null, null));

		mockMvc.perform(patch("/api/v1/links/{linkId}/signals/{signalId}", linkId, templateSignal.getId())
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("SIGNAL_NOT_EDITABLE"));
	}

	@Test
	void updateRejectsARequesterWhoIsNotAGuardianWith403() throws Exception {
		UUID linkId = UUID.randomUUID();
		String body = objectMapper.writeValueAsString(
				new UpdateSignalRequest("바꾼 이름", null, null, null, null));

		mockMvc.perform(patch("/api/v1/links/{linkId}/signals/{signalId}", linkId, UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "RECIPIENT"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void deleteReturns404ForASignalFromADifferentLink() throws Exception {
		UUID actualLinkId = UUID.randomUUID();
		Signal signal = signalRepository.saveAndFlush(Signal.createCustom(actualLinkId, "신호", null,
				SignalCategory.CHECK, "icon", List.of(RhythmBeat.SHORT, RhythmBeat.LONG)));

		mockMvc.perform(delete("/api/v1/links/{linkId}/signals/{signalId}", UUID.randomUUID(), signal.getId())
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID())))
				.andExpect(status().isNotFound());
	}

	@Test
	void deleteRejectsARequesterWhoIsNotAGuardianWith403() throws Exception {
		mockMvc.perform(delete("/api/v1/links/{linkId}/signals/{signalId}", UUID.randomUUID(), UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "RECIPIENT")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}
}
