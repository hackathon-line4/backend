package com.sonulim.domain.link.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.domain.signal.domain.SignalTemplate;
import com.sonulim.domain.signal.domain.SignalTemplateRepository;

/**
 * 승인하면 그 연결에 기본 신호가 채워진다(#78). 실제 signal 구현(SignalTemplateSeeder)을 그대로 거친다.
 * 신호 확인을 위해 테스트에서만 signal의 리포지토리를 읽는다(link 본 코드는 global.signalseed 인터페이스만 의존).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LinkApproveSeedsDefaultSignalsIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private SignalTemplateRepository signalTemplateRepository;

	@Autowired
	private SignalRepository signalRepository;

	private LinkApiTestSupport support;

	@BeforeEach
	void setUp() {
		support = new LinkApiTestSupport(mockMvc, objectMapper);
	}

	@Test
	void approvingFillsTheNewLinkWithEveryTemplateInTheCatalog() throws Exception {
		// 다른 테스트가 카탈로그를 비웠을 수 있어 하나는 확실히 넣는다. rhythm이 없어 기존 템플릿과 리듬 충돌이 나지 않는다.
		signalTemplateRepository.saveAndFlush(new SignalTemplate("승인 테스트 신호", null, SignalCategory.DAILY, null,
				null, List.of(150, 150, 700)));
		long templateCount = signalTemplateRepository.count();

		String guardian = support.signupAs("lrs-seed-g@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String recipient = support.signupAs("lrs-seed-r@example.com", "김하늘", "RECIPIENT");
		String requestId = support.sentRequestId(recipient, code);

		String body = support.decide(guardian, requestId, "approve")
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		UUID linkId = UUID.fromString(JsonPath.read(body, "$.data.link.id"));

		List<Signal> signals = signalRepository.findByLinkIdAndDeletedAtIsNull(linkId);
		assertThat(signals).hasSize((int) templateCount);
		assertThat(signals).extracting(Signal::getName).contains("승인 테스트 신호");
	}
}
