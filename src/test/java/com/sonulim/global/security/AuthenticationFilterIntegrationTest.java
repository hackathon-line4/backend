package com.sonulim.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.global.exception.ApiResponse;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthenticationFilterIntegrationTest.ProtectedTestController.class)
class AuthenticationFilterIntegrationTest {

	private static final String PROTECTED_PATH = "/api/v1/test/protected";

	// 도메인 API와 관계없이 인증 필터 동작만 확인하기 위한 보호 경로
	@RestController
	static class ProtectedTestController {

		@GetMapping(PROTECTED_PATH)
		ApiResponse<Map<String, String>> protectedEndpoint(@AuthenticationPrincipal AuthenticatedUser user) {
			return ApiResponse.success(Map.of("userId", user.userId().toString(), "role", user.role()));
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private JwtProperties jwtProperties;

	@Test
	void validTokenReachesProtectedPathWithItsUser() throws Exception {
		UUID userId = UUID.randomUUID();
		String token = jwtTokenProvider.createAccessToken(userId, "GUARDIAN");

		mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(userId.toString()))
				.andExpect(jsonPath("$.data.role").value("GUARDIAN"));
	}

	@Test
	void missingTokenReturns401Unauthorized() throws Exception {
		mockMvc.perform(get(PROTECTED_PATH))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.ok").value(false))
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.error.message").value("다시 로그인해 주세요"));
	}

	@Test
	void malformedTokenReturns401Unauthorized() throws Exception {
		mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void expiredTokenReturns401TokenExpired() throws Exception {
		Clock twoHoursAgo = Clock.fixed(Instant.now().minus(2, ChronoUnit.HOURS), ZoneOffset.UTC);
		String expired = new JwtTokenProvider(jwtProperties, twoHoursAgo).createAccessToken(UUID.randomUUID(), null);

		mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"))
				.andExpect(jsonPath("$.error.message").value("토큰이 만료되었습니다"));
	}
}
