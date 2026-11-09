package com.sonulim.domain.signal.api;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.domain.signal.domain.SignalTemplate;
import com.sonulim.domain.signal.domain.SignalTemplateRepository;
import com.sonulim.global.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SignalTemplateControllerTest {

	@Autowired
	private org.springframework.test.web.servlet.MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private SignalTemplateRepository signalTemplateRepository;

	@Autowired
	private SignalRepository signalRepository;

	private String bearerTokenFor(UUID userId, String role) {
		return "Bearer " + jwtTokenProvider.createAccessToken(userId, role);
	}

	// signal_templates는 전역 카탈로그라(@SpringBootTest는 테스트 간 롤백이 없음) 각 테스트
	// 뒤에 직접 치운다 — 안 그러면 "개수가 1개"류 단언이 다른 테스트의 잔여 데이터와 섞인다.
	@AfterEach
	void cleanUpTemplates() {
		signalTemplateRepository.deleteAll();
	}

	@Test
	void returnsTemplatesWithAlreadyAddedFalseWhenNoLinkIdIsGiven() throws Exception {
		signalTemplateRepository.saveAndFlush(new SignalTemplate("가자", "함께 이동하자는 뜻", SignalCategory.OUTING, "walk",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG)));

		mockMvc.perform(get("/api/v1/signal-templates")
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[?(@.name=='가자')].alreadyAdded").value(false));
	}

	@Test
	void marksAlreadyAddedTrueWhenTheLinkAlreadyHasThatTemplate() throws Exception {
		UUID linkId = UUID.randomUUID();
		SignalTemplate template = signalTemplateRepository.saveAndFlush(new SignalTemplate("가자", null,
				SignalCategory.OUTING, "walk", List.of(RhythmBeat.LONG, RhythmBeat.LONG)));
		signalRepository.saveAndFlush(Signal.fromTemplate(linkId, template.getId(), "가자", null, SignalCategory.OUTING,
				"walk", List.of(RhythmBeat.LONG, RhythmBeat.LONG)));

		mockMvc.perform(get("/api/v1/signal-templates")
						.queryParam("linkId", linkId.toString())
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[?(@.id=='" + template.getId() + "')].alreadyAdded").value(true));
	}

	@Test
	void filtersByQAndCategory() throws Exception {
		signalTemplateRepository.saveAndFlush(new SignalTemplate("가자", "함께 이동하자는 뜻", SignalCategory.OUTING, "walk",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG)));
		signalTemplateRepository.saveAndFlush(new SignalTemplate("밥 먹자", "식사하러 오라는 뜻", SignalCategory.DAILY, "meal",
				List.of(RhythmBeat.SHORT, RhythmBeat.SHORT)));

		mockMvc.perform(get("/api/v1/signal-templates")
						.queryParam("category", "DAILY")
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "GUARDIAN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].name").value("밥 먹자"));
	}

	@Test
	void rejectsARequesterWhoIsNotAGuardianWith403() throws Exception {
		mockMvc.perform(get("/api/v1/signal-templates")
						.header(HttpHeaders.AUTHORIZATION, bearerTokenFor(UUID.randomUUID(), "RECIPIENT")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/signal-templates"))
				.andExpect(status().isUnauthorized());
	}
}
