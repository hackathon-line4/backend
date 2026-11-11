package com.sonulim.domain.settings.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.settings.application.PartnerSettingsService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "설정")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
public class PartnerSettingsController {

	private final PartnerSettingsService partnerSettingsService;

	public PartnerSettingsController(PartnerSettingsService partnerSettingsService) {
		this.partnerSettingsService = partnerSettingsService;
	}

	@Operation(summary = "당사자 설정 보기", description = "C-1, T-3 · 보호자만. 당사자의 진동 설정과 위치 공유 상태를 읽기 전용으로 "
			+ "돌려준다. 남의 연결·없는 연결은 404")
	@GetMapping("/api/v1/links/{linkId}/partner-settings")
	public ApiResponse<PartnerSettingsResponse> get(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable String linkId) {
		return ApiResponse.success(PartnerSettingsResponse.from(partnerSettingsService.get(user, linkId)));
	}
}
