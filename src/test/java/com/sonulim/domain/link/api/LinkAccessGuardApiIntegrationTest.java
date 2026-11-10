package com.sonulim.domain.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkRepository;
import com.sonulim.global.security.JwtTokenProvider;

/**
 * 다른 도메인 API가 실제 연결 접근 검증을 거치는지 — signal의 신호 목록(조회 API, verifyAccessible)으로 확인한다.
 * signal 코드는 건드리지 않고 부르기만 한다. 통과용 설정(PassThroughLinkAccessGuardConfig)은 붙이지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LinkAccessGuardApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private LinkRepository linkRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private final UUID guardianId = UUID.randomUUID();
	private final UUID recipientId = UUID.randomUUID();

	@Test
	void guardianOfTheLinkCanListSignals() throws Exception {
		Link link = linkRepository.saveAndFlush(Link.connect(guardianId, recipientId, LocalDateTime.now()));

		listSignals(link.getId(), guardianId).andExpect(status().isOk());
	}

	@Test
	void guardianOfAnotherLinkOrUnknownLinkGets404() throws Exception {
		Link link = linkRepository.saveAndFlush(Link.connect(guardianId, recipientId, LocalDateTime.now()));

		listSignals(link.getId(), UUID.randomUUID())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
		listSignals(UUID.randomUUID(), guardianId)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	// 조회 API는 해제된 연결에도 409를 내지 않는다(#75).
	@Test
	void endedLinkCanStillBeListedByItsGuardian() throws Exception {
		Link link = linkRepository.saveAndFlush(Link.connect(guardianId, recipientId, LocalDateTime.now()));
		link.end(LocalDateTime.now());
		linkRepository.saveAndFlush(link);

		listSignals(link.getId(), guardianId).andExpect(status().isOk());
	}

	private ResultActions listSignals(UUID linkId, UUID userId) throws Exception {
		return mockMvc.perform(get("/api/v1/links/" + linkId + "/signals")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(userId, "GUARDIAN")));
	}
}
