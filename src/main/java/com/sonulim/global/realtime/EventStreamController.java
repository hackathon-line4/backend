package com.sonulim.global.realtime;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.security.AuthenticatedUser;
import com.sonulim.global.security.JwtTokenProvider;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "실시간")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
public class EventStreamController {

	private static final String BEARER_PREFIX = "Bearer ";

	private final SseEventHub sseEventHub;
	private final JwtTokenProvider tokenProvider;

	public EventStreamController(SseEventHub sseEventHub, JwtTokenProvider tokenProvider) {
		this.sseEventHub = sseEventHub;
		this.tokenProvider = tokenProvider;
	}

	@Operation(summary = "이벤트 스트림(SSE)", description = "공통. 앱이 열려 있는 동안 상태 변화를 받는다. 사용자별 연결 1개"
			+ "(다시 연결하면 이전 연결을 닫음), 20초마다 `: ping`. 재연결 때 Last-Event-ID를 보내면 최근 5분 안의 놓친 "
			+ "이벤트를 다시 보낸다. 액세스 토큰이 만료되면 연결을 닫는다(다시 연결하면 401 TOKEN_EXPIRED)")
	@GetMapping(path = "/api/v1/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
			@RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
		// 인증 필터를 통과했으므로 Bearer 토큰이 있다.
		String accessToken = authorization.substring(BEARER_PREFIX.length());
		return sseEventHub.connect(user.userId(), tokenProvider.expiresAt(accessToken), lastEventId);
	}
}
