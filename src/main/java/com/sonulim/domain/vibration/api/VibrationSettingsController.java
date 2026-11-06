package com.sonulim.domain.vibration.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.vibration.application.VibrationSettingsService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "설정")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/me/vibration-settings")
public class VibrationSettingsController {

	private final VibrationSettingsService vibrationSettingsService;

	public VibrationSettingsController(VibrationSettingsService vibrationSettingsService) {
		this.vibrationSettingsService = vibrationSettingsService;
	}

	@Operation(summary = "내 진동 설정 조회", description = "C-1 · 공통. 저장한 적이 없으면 기본값(NORMAL, NORMAL, 2)과 updatedAt null")
	@GetMapping
	public ApiResponse<VibrationSettingsResponse> get(@AuthenticationPrincipal AuthenticatedUser user) {
		return ApiResponse.success(VibrationSettingsResponse.from(vibrationSettingsService.get(user.userId())));
	}
}
