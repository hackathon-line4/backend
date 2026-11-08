package com.sonulim.domain.signal.api;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RhythmCheckControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private SignalRepository signalRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private String bearerTokenFor(UUID userId, String role) {
		return "Bearer " + jwtTokenProvider.createAccessToken(userId, role);
	}

	@Test
	void reportsValidWithDurationWhenNothingMatches() throws Exception {
		UUID linkId = UUID.randomUUID();
		String body = objectMapper.writeValueAsString(new RhythmCheckRequest(List.of("SHORT", "LONG"), null));

		mockMvc.perform(post("/api/v1/links/{linkId}/signals/rhythm-check", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.valid").value(true))
				.andExpect(jsonPath("$.data.durationMs").value(1400))
				.andExpect(jsonPath("$.data.conflictSignal").doesNotExist());
	}

	@Test
	void reportsInvalidWithConflictingSignal() throws Exception {
		UUID linkId = UUID.randomUUID();
		Signal existing = signalRepository.saveAndFlush(Signal.createCustom(linkId, "가자", null,
				SignalCategory.OUTING, "icon", List.of(RhythmBeat.SHORT, RhythmBeat.LONG)));
		String body = objectMapper.writeValueAsString(new RhythmCheckRequest(List.of("SHORT", "LONG"), null));

		mockMvc.perform(post("/api/v1/links/{linkId}/signals/rhythm-check", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.valid").value(false))
				.andExpect(jsonPath("$.data.conflictSignal.id").value(existing.getId().toString()))
				.andExpect(jsonPath("$.data.conflictSignal.name").value("가자"));
	}

	@Test
	void excludeSignalIdLeavesTheSignalBeingEditedOutOfTheComparison() throws Exception {
		UUID linkId = UUID.randomUUID();
		Signal beingEdited = signalRepository.saveAndFlush(Signal.createCustom(linkId, "가자", null,
				SignalCategory.OUTING, "icon", List.of(RhythmBeat.SHORT, RhythmBeat.LONG)));
		String body = objectMapper.writeValueAsString(
				new RhythmCheckRequest(List.of("SHORT", "LONG"), beingEdited.getId()));

		mockMvc.perform(post("/api/v1/links/{linkId}/signals/rhythm-check", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.valid").value(true));
	}

	@Test
	void rejectsAnInvalidRhythmWith400() throws Exception {
		UUID linkId = UUID.randomUUID();
		String body = objectMapper.writeValueAsString(new RhythmCheckRequest(List.of("SHORT"), null));

		mockMvc.perform(post("/api/v1/links/{linkId}/signals/rhythm-check", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("RHYTHM_INVALID"));
	}

	@Test
	void rejectsARequesterWhoIsNotAGuardianWith403() throws Exception {
		UUID linkId = UUID.randomUUID();
		String body = objectMapper.writeValueAsString(new RhythmCheckRequest(List.of("SHORT", "LONG"), null));

		mockMvc.perform(post("/api/v1/links/{linkId}/signals/rhythm-check", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "RECIPIENT"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void requiresAuthentication() throws Exception {
		UUID linkId = UUID.randomUUID();
		String body = objectMapper.writeValueAsString(new RhythmCheckRequest(List.of("SHORT", "LONG"), null));

		mockMvc.perform(post("/api/v1/links/{linkId}/signals/rhythm-check", linkId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isUnauthorized());
	}
}
