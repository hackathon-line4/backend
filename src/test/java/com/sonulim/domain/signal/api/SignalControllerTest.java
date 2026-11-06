package com.sonulim.domain.signal.api;

import java.util.List;
import java.util.UUID;

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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

	private String bearerTokenFor(UUID userId) {
		return "Bearer " + jwtTokenProvider.createAccessToken(userId, "GUARDIAN");
	}

	@Test
	void listReturnsEmptyArrayWhenTheLinkHasNoSignals() throws Exception {
		UUID linkId = UUID.randomUUID();

		mockMvc.perform(get("/api/v1/links/{linkId}/signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(true))
				.andExpect(jsonPath("$.data").isArray())
				.andExpect(jsonPath("$.data").isEmpty());
	}

	@Test
	void listReturnsSignalsBelongingToThatLinkWithCorrectShape() throws Exception {
		UUID linkId = UUID.randomUUID();
		signalRepository.saveAndFlush(Signal.createCustom(linkId, "약 먹을 시간", "저녁 약을 먹자는 뜻",
				SignalCategory.DAILY, "pill", List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG)));

		mockMvc.perform(get("/api/v1/links/{linkId}/signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].name").value("약 먹을 시간"))
				.andExpect(jsonPath("$.data[0].source").value("CUSTOM"))
				.andExpect(jsonPath("$.data[0].category").value("DAILY"))
				.andExpect(jsonPath("$.data[0].rhythm[0]").value("SHORT"))
				.andExpect(jsonPath("$.data[0].durationMs").value(1900))
				.andExpect(jsonPath("$.data[0].practiced").value(false));
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
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/links/{linkId}/signals", UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
	}
}
