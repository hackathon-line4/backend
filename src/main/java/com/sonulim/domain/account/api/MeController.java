package com.sonulim.domain.account.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.account.application.MeService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "인증·계정")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

	private final MeService meService;

	public MeController(MeService meService) {
		this.meService = meService;
	}

	@Operation(summary = "내 정보 조회", description = "A-2 · 공통(역할 없어도 호출 가능). 자동 로그인·앱 시작 화면 분기용")
	@GetMapping
	public ApiResponse<MeResponse> getMe(@AuthenticationPrincipal AuthenticatedUser user) {
		return ApiResponse.success(MeResponse.from(meService.getMe(user.userId())));
	}
}
