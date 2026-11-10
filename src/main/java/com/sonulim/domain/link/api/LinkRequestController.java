package com.sonulim.domain.link.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.link.application.LinkRequestService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "연결")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/link-requests")
public class LinkRequestController {

	private final LinkRequestService linkRequestService;

	public LinkRequestController(LinkRequestService linkRequestService) {
		this.linkRequestService = linkRequestService;
	}

	@Operation(summary = "연결 요청 보내기", description = "L-2 · 당사자. 코드 검사는 코드 확인 API와 같다. 요청은 10분 동안 유효. "
			+ "같은 당사자의 이전 대기 요청은 CANCELED. 같은 Idempotency-Key로 재시도하면 같은 요청을 돌려준다")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<LinkRequestResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody CreateLinkRequestRequest request) {
		return ApiResponse.success(
				LinkRequestResponse.from(linkRequestService.create(user, idempotencyKey, request.code())));
	}

	@Operation(summary = "연결 요청 조회", description = "L-2 · 요청의 보호자·당사자. 아니면 404. 만료 시각이 지났으면 EXPIRED로 돌려준다")
	@GetMapping("/{requestId}")
	public ApiResponse<LinkRequestResponse> get(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable String requestId) {
		return ApiResponse.success(LinkRequestResponse.from(linkRequestService.get(user, requestId)));
	}

	@Operation(summary = "연결 요청 취소", description = "L-2 · 당사자. PENDING일 때만. 아니면 409 LINK_REQUEST_NOT_PENDING"
			+ "(details.status에 현재 상태)")
	@PostMapping("/{requestId}/cancel")
	public ApiResponse<LinkRequestResponse> cancel(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable String requestId) {
		return ApiResponse.success(LinkRequestResponse.from(linkRequestService.cancel(user, requestId)));
	}

	@Operation(summary = "받은 연결 요청 목록", description = "L-3 · 보호자. 내가 보호자인 요청만 최신순. "
			+ "status를 생략하면 최근 24시간 전체")
	@GetMapping
	public ApiResponse<LinkRequestListResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) String status) {
		return ApiResponse.success(LinkRequestListResponse.from(linkRequestService.list(user, status)));
	}

	@Operation(summary = "연결 요청 승인", description = "L-3 · 보호자. 연결을 만들고 코드를 사용 처리한다. PENDING이 아니면 "
			+ "409 LINK_REQUEST_NOT_PENDING, 그사이 다른 연결이 생겼으면 409 ALREADY_LINKED")
	@PostMapping("/{requestId}/approve")
	public ApiResponse<LinkRequestResponse> approve(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable String requestId) {
		return ApiResponse.success(LinkRequestResponse.from(linkRequestService.approve(user, requestId)));
	}

	@Operation(summary = "연결 요청 거절", description = "L-3 · 보호자. PENDING일 때만. 코드는 그대로 둔다(만료 전이면 다시 쓸 수 있음)")
	@PostMapping("/{requestId}/reject")
	public ApiResponse<LinkRequestResponse> reject(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable String requestId) {
		return ApiResponse.success(LinkRequestResponse.from(linkRequestService.reject(user, requestId)));
	}
}
