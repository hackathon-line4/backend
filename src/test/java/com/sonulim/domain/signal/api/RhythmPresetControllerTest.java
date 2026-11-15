package com.sonulim.domain.signal.api;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
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

import static org.assertj.core.api.Assertions.assertThat;
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
		// MVP 프리셋 8종엔 없는 조합을 써서 RhythmPresetCatalogSeeder가 채운 데이터와 안 겹치게 한다.
		RhythmPreset preset = rhythmPresetRepository
				.saveAndFlush(new RhythmPreset(List.of(RhythmBeat.MEDIUM, RhythmBeat.MEDIUM)));

		String body = mockMvc.perform(get("/api/v1/rhythm-presets")
						.queryParam("linkId", linkId.toString())
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		Map<String, Object> item = itemWithId(body, preset.getId());
		assertThat((List<Object>) item.get("rhythm")).containsExactly("MEDIUM", "MEDIUM");
		assertThat(item.get("usedBy")).isNull();
	}

	@Test
	void reportsUsedByTheConflictingSignal() throws Exception {
		UUID linkId = UUID.randomUUID();
		RhythmPreset preset = rhythmPresetRepository
				.saveAndFlush(new RhythmPreset(List.of(RhythmBeat.LONG, RhythmBeat.LONG)));
		Signal signal = signalRepository.saveAndFlush(Signal.createCustom(linkId, "가자", null, SignalCategory.OUTING,
				"icon", List.of(RhythmBeat.LONG, RhythmBeat.LONG)));

		String body = mockMvc.perform(get("/api/v1/rhythm-presets")
						.queryParam("linkId", linkId.toString())
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		Map<?, ?> usedBy = (Map<?, ?>) itemWithId(body, preset.getId()).get("usedBy");
		assertThat(usedBy.get("signalId")).isEqualTo(signal.getId().toString());
		assertThat(usedBy.get("name")).isEqualTo("가자");
	}

	/** rhythm_presets는 전역 카탈로그라 응답에 이 테스트가 만든 것 말고도 다른 프리셋이 섞여 있다. */
	private Map<String, Object> itemWithId(String body, UUID id) {
		List<Map<String, Object>> items = JsonPath.read(body, "$.data.items");
		return items.stream().filter(item -> id.toString().equals(item.get("id"))).findFirst().orElseThrow();
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
