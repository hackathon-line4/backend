package com.sonulim.domain.link.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.link.application.LinkCodeService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "연결")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/link-codes")
public class LinkCodeController {

	private final LinkCodeService linkCodeService;

	public LinkCodeController(LinkCodeService linkCodeService) {
		this.linkCodeService = linkCodeService;
	}

	@Operation(summary = "연결 코드 만들기", description = "L-1 · 보호자. 6자리 숫자 코드와 QR 내용을 만든다. 유효 시간 10분. "
			+ "새로 만들면 이전 코드는 바로 무효. 같은 Idempotency-Key로 재시도하면 같은 코드를 돌려준다")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<LinkCodeResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
		return ApiResponse.success(LinkCodeResponse.from(linkCodeService.create(user, idempotencyKey)));
	}

	@Operation(summary = "현재 코드 조회", description = "L-1 · 보호자. 살아 있는 코드가 없으면 404 NOT_FOUND")
	@GetMapping("/current")
	public ApiResponse<LinkCodeResponse> getCurrent(@AuthenticationPrincipal AuthenticatedUser user) {
		return ApiResponse.success(LinkCodeResponse.from(linkCodeService.getCurrent(user)));
	}

	@Operation(summary = "코드 확인", description = "L-2 · 당사자. 코드를 만든 보호자를 돌려준다. 연결 요청은 만들지 않는다. "
			+ "10분에 10회 틀리면 429 TOO_MANY_ATTEMPTS")
	@PostMapping("/verify")
	public ApiResponse<VerifyLinkCodeResponse> verify(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody VerifyLinkCodeRequest request) {
		return ApiResponse.success(VerifyLinkCodeResponse.from(linkCodeService.verify(user, request.code())));
	}
}
