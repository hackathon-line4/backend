package com.sonulim.domain.settings.api;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.settings.application.LocationSharingService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "설정")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/me/location-sharing")
public class LocationSharingController {

	private final LocationSharingService locationSharingService;

	public LocationSharingController(LocationSharingService locationSharingService) {
		this.locationSharingService = locationSharingService;
	}

	@Operation(summary = "위치 공유 설정 조회", description = "T-3 · 당사자만. 한 번도 바꾸지 않았으면 updatedAt은 null")
	@GetMapping
	public ApiResponse<LocationSharingResponse> get(@AuthenticationPrincipal AuthenticatedUser user) {
		return ApiResponse.success(LocationSharingResponse.from(locationSharingService.get(user)));
	}

	@Operation(summary = "위치 공유 설정 변경", description = "T-3 · 당사자만. 바꾼 시각을 updatedAt으로 남긴다")
	@PutMapping
	public ApiResponse<LocationSharingResponse> update(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody LocationSharingRequest request) {
		return ApiResponse.success(LocationSharingResponse.from(locationSharingService.update(user, request.enabled())));
	}
}
