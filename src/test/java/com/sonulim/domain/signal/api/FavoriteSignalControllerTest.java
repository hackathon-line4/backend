package com.sonulim.domain.signal.api;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonulim.domain.signal.domain.FavoriteSignalRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FavoriteSignalControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private SignalRepository signalRepository;

	@Autowired
	private FavoriteSignalRepository favoriteSignalRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private String bearerTokenFor(UUID userId, String role) {
		return "Bearer " + jwtTokenProvider.createAccessToken(userId, role);
	}

	private Signal saveSignal(UUID linkId, String name) {
		return signalRepository.saveAndFlush(Signal.createCustom(linkId, name, null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG)));
	}

	@Test
	void replacesFavoritesAndReturnsThemInTheGivenOrder() throws Exception {
		UUID linkId = UUID.randomUUID();
		Signal first = saveSignal(linkId, "가자");
		Signal second = saveSignal(linkId, "밥 먹자");
		String body = objectMapper
				.writeValueAsString(new ReplaceFavoriteSignalsRequest(List.of(second.getId(), first.getId())));

		mockMvc.perform(put("/api/v1/links/{linkId}/favorite-signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.items[0].id").value(second.getId().toString()))
				.andExpect(jsonPath("$.data.items[1].id").value(first.getId().toString()));
	}

	@Test
	void replacingTwiceFullyOverwritesThePreviousSet() throws Exception {
		UUID linkId = UUID.randomUUID();
		Signal first = saveSignal(linkId, "가자");
		Signal second = saveSignal(linkId, "밥 먹자");
		String token = bearerTokenFor(UUID.randomUUID(), "GUARDIAN");
		mockMvc.perform(put("/api/v1/links/{linkId}/favorite-signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new ReplaceFavoriteSignalsRequest(List.of(first.getId(), second.getId())))))
				.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/links/{linkId}/favorite-signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper
								.writeValueAsString(new ReplaceFavoriteSignalsRequest(List.of(second.getId())))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].id").value(second.getId().toString()));
		org.assertj.core.api.Assertions.assertThat(favoriteSignalRepository.findByLinkIdOrderByPosition(linkId))
				.hasSize(1);
	}

	@Test
	void emptyListClearsFavorites() throws Exception {
		UUID linkId = UUID.randomUUID();
		String body = objectMapper.writeValueAsString(new ReplaceFavoriteSignalsRequest(List.of()));

		mockMvc.perform(put("/api/v1/links/{linkId}/favorite-signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@Test
	void rejectsMoreThanSixSignalIdsWith400() throws Exception {
		UUID linkId = UUID.randomUUID();
		List<UUID> sevenIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		String body = objectMapper.writeValueAsString(new ReplaceFavoriteSignalsRequest(sevenIds));

		mockMvc.perform(put("/api/v1/links/{linkId}/favorite-signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("FAVORITE_LIMIT_EXCEEDED"))
				.andExpect(jsonPath("$.error.field").value("signalIds"));
	}

	@Test
	void rejectsDuplicateSignalIdsWith400() throws Exception {
		UUID linkId = UUID.randomUUID();
		Signal signal = saveSignal(linkId, "가자");
		String body = objectMapper
				.writeValueAsString(new ReplaceFavoriteSignalsRequest(List.of(signal.getId(), signal.getId())));

		mockMvc.perform(put("/api/v1/links/{linkId}/favorite-signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
	}

	@Test
	void rejectsASignalIdFromAnotherLinkWith404() throws Exception {
		UUID linkId = UUID.randomUUID();
		UUID otherLinkId = UUID.randomUUID();
		Signal signalFromOtherLink = saveSignal(otherLinkId, "다른 연결 신호");
		String body = objectMapper
				.writeValueAsString(new ReplaceFavoriteSignalsRequest(List.of(signalFromOtherLink.getId())));

		mockMvc.perform(put("/api/v1/links/{linkId}/favorite-signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void rejectsADeletedSignalWith404() throws Exception {
		UUID linkId = UUID.randomUUID();
		Signal signal = saveSignal(linkId, "가자");
		signal.softDelete();
		signalRepository.saveAndFlush(signal);
		String body = objectMapper.writeValueAsString(new ReplaceFavoriteSignalsRequest(List.of(signal.getId())));

		mockMvc.perform(put("/api/v1/links/{linkId}/favorite-signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void rejectsARequesterWhoIsNotAGuardianWith403() throws Exception {
		UUID linkId = UUID.randomUUID();
		String body = objectMapper.writeValueAsString(new ReplaceFavoriteSignalsRequest(List.of()));

		mockMvc.perform(put("/api/v1/links/{linkId}/favorite-signals", linkId)
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "RECIPIENT"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void requiresAuthentication() throws Exception {
		UUID linkId = UUID.randomUUID();
		String body = objectMapper.writeValueAsString(new ReplaceFavoriteSignalsRequest(List.of()));

		mockMvc.perform(put("/api/v1/links/{linkId}/favorite-signals", linkId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isUnauthorized());
	}
}
