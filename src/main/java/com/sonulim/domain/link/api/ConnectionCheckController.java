package com.sonulim.domain.link.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.link.application.ConnectionCheckService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 연결 확인(T-2). 기능명세서상 설정(9장)이지만 실제 태그가 [연결]이라 link 도메인에 둔다(docs/conventions.md 예외 표).
 */
@Tag(name = "연결")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
public class ConnectionCheckController {

	private final ConnectionCheckService connectionCheckService;

	public ConnectionCheckController(ConnectionCheckService connectionCheckService) {
		this.connectionCheckService = connectionCheckService;
	}

	@Operation(summary = "연결 확인 요청", description = "T-2 · 연결의 보호자·당사자. 30초(제안) 안에 상대 앱의 ack가 오면 "
			+ "CONFIRMED, 아니면 UNCONFIRMED. 같은 연결에 PENDING 확인이 있으면 그것을 돌려준다. 해제된 연결은 404")
	@PostMapping("/api/v1/links/{linkId}/connection-checks")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public ApiResponse<ConnectionCheckResponse> request(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable String linkId) {
		return ApiResponse.success(ConnectionCheckResponse.from(connectionCheckService.request(user, linkId)));
	}

	@Operation(summary = "연결 확인 응답(ack)", description = "T-2 · 확인 요청을 받은 쪽 앱이 자동으로 보낸다. 요청한 사람은 404. "
			+ "여러 번 와도 처음 한 번만 기록. 타임아웃 뒤에 와도 연결의 lastConfirmedAt은 갱신")
	@PostMapping("/api/v1/connection-checks/{checkId}/ack")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void ack(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String checkId) {
		connectionCheckService.ack(user, checkId);
	}

	@Operation(summary = "연결 확인 결과 조회", description = "T-2 · 연결의 보호자·당사자. SSE가 끊겼을 때 폴링용. "
			+ "timeoutAt이 지났는데 PENDING이면 UNCONFIRMED로 돌려준다")
	@GetMapping("/api/v1/connection-checks/{checkId}")
	public ApiResponse<ConnectionCheckResponse> get(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable String checkId) {
		return ApiResponse.success(ConnectionCheckResponse.from(connectionCheckService.get(user, checkId)));
	}
}
