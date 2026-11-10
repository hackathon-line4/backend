package com.sonulim.domain.link.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonulim.domain.link.domain.LinkCodeRepository;
import com.sonulim.domain.link.domain.LinkRepository;
import com.sonulim.global.signalseed.DefaultSignalSeeder;

/**
 * 기본 신호 채우기가 실패하면 승인 전체가 롤백된다(#78) — 연결이 생기지 않고 요청은 PENDING, 코드도 쓰지 않은 상태.
 *
 * <p>롤백을 실제로 확인하려고 클래스에 @Transactional을 붙이지 않는다(붙이면 승인이 테스트 트랜잭션에 합류해
 * 롤백 전 상태가 그대로 보인다). 그래서 매번 다른 이메일로 가입하고, 남은 행은 다른 테스트와 겹치지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LinkApproveSeedFailureIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private LinkRepository linkRepository;

	@Autowired
	private LinkCodeRepository linkCodeRepository;

	@MockBean
	private DefaultSignalSeeder defaultSignalSeeder;

	private LinkApiTestSupport support;

	@BeforeEach
	void setUp() {
		support = new LinkApiTestSupport(mockMvc, objectMapper);
	}

	@Test
	void seederFailureRollsBackTheWholeApproval() throws Exception {
		willThrow(new IllegalStateException("기본 신호 채우기 실패")).given(defaultSignalSeeder).seedDefaults(any());
		String suffix = UUID.randomUUID().toString();
		String guardian = support.signupAs("lrs-fail-g-" + suffix + "@example.com", "이영희", "GUARDIAN");
		String code = support.createdCode(guardian);
		String recipient = support.signupAs("lrs-fail-r-" + suffix + "@example.com", "김하늘", "RECIPIENT");
		String requestId = support.sentRequestId(recipient, code);

		support.decide(guardian, requestId, "approve")
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));

		support.getRequest(recipient, requestId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.link").value(nullValue()));
		assertThat(linkRepository.existsByActiveGuardianId(UUID.fromString(support.userId(guardian)))).isFalse();
		assertThat(linkCodeRepository.findLatestNotRevokedByCode(code).orElseThrow().getUsedAt()).isNull();
	}
}
