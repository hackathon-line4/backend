package com.sonulim.domain.signal.api;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.RhythmPreset;
import com.sonulim.domain.signal.domain.RhythmPresetRepository;
import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.global.linkaccess.PassThroughLinkAccessGuardConfig;
import com.sonulim.global.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PassThroughLinkAccessGuardConfig.class)
class RhythmPresetControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private RhythmPresetRepository rhythmPresetRepository;

	@Autowired
	private SignalRepository signalRepository;

	// rhythm_presets는 전역 카탈로그라(@SpringBootTest는 테스트 간 롤백이 없음) 각 테스트 뒤에
	// 직접 치운다.
	@AfterEach
	void cleanUpPresets() {
		rhythmPresetRepository.deleteAll();
	}

	private String bearerTokenFor(UUID userId, String role) {
		return "Bearer " + jwtTokenProvider.createAccessToken(userId, role);
	}

	@Test
	void reportsUsedByNullWhenNoSignalUsesThatRhythm() throws Exception {
		UUID linkId = UUID.randomUUID();
		rhythmPresetRepository.saveAndFlush(new RhythmPreset(List.of(RhythmBeat.SHORT, RhythmBeat.SHORT)));

		mockMvc.perform(get("/api/v1/rhythm-presets")
						.queryParam("linkId", linkId.toString())
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].rhythm[0]").value("SHORT"))
				.andExpect(jsonPath("$.data.items[0].usedBy").doesNotExist());
	}

	@Test
	void reportsUsedByTheConflictingSignal() throws Exception {
		UUID linkId = UUID.randomUUID();
		rhythmPresetRepository.saveAndFlush(new RhythmPreset(List.of(RhythmBeat.LONG, RhythmBeat.LONG)));
		Signal signal = signalRepository.saveAndFlush(Signal.createCustom(linkId, "가자", null, SignalCategory.OUTING,
				"icon", List.of(RhythmBeat.LONG, RhythmBeat.LONG)));

		mockMvc.perform(get("/api/v1/rhythm-presets")
						.queryParam("linkId", linkId.toString())
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].usedBy.signalId").value(signal.getId().toString()))
				.andExpect(jsonPath("$.data.items[0].usedBy.name").value("가자"));
	}

	@Test
	void requiresTheLinkIdQueryParamWith400() throws Exception {
		mockMvc.perform(get("/api/v1/rhythm-presets")
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("linkId"));
	}

	@Test
	void rejectsARequesterWhoIsNotAGuardianWith403() throws Exception {
		mockMvc.perform(get("/api/v1/rhythm-presets")
						.queryParam("linkId", UUID.randomUUID().toString())
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "RECIPIENT")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/rhythm-presets").queryParam("linkId", UUID.randomUUID().toString()))
				.andExpect(status().isUnauthorized());
	}
}
