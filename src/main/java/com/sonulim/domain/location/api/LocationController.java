package com.sonulim.domain.location.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.location.application.LocationViewService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "위치")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/links/{linkId}")
public class LocationController {

	private final LocationViewService locationViewService;

	public LocationController(LocationViewService locationViewService) {
		this.locationViewService = locationViewService;
	}

	@Operation(summary = "위치 새로고침 요청", description = "P-1 · 보호자만. 당사자 휴대폰에 현재 위치를 1회 요청한다. "
			+ "진행 중인 요청이 있으면 새로 만들지 않고 그것을 돌려준다 — 버튼을 여러 번 눌러도 "
			+ "당사자 기기가 여러 번 깨지 않는다. 공유가 꺼져 있으면 409 LOCATION_SHARING_OFF")
	@PostMapping("/location-requests")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public ApiResponse<LocationRequestResponse> refresh(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID linkId) {
		return ApiResponse.success(LocationRequestResponse.from(locationViewService.refresh(user, linkId)));
	}

	@Operation(summary = "마지막 위치 조회", description = "P-1 · 보호자만. 당사자의 마지막 위치 1건과 공유 상태. "
			+ "공유가 꺼져 있으면 last는 항상 null이다 — 저장된 값이 남아 있어도 보여 주지 않는다. "
			+ "pendingRequest는 진행 중인 새로고침 요청")
	@GetMapping("/location")
	public ApiResponse<LocationViewResponse> view(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID linkId) {
		return ApiResponse.success(LocationViewResponse.from(locationViewService.view(user, linkId)));
	}
}
