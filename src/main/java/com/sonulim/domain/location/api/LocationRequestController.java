package com.sonulim.domain.location.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.location.application.LocationRequestService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "위치")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/location-requests")
public class LocationRequestController {

	private final LocationRequestService locationRequestService;

	public LocationRequestController(LocationRequestService locationRequestService) {
		this.locationRequestService = locationRequestService;
	}

	@Operation(summary = "새로고침 요청 상태", description = "P-1 · 보호자만. SSE가 끊겼을 때 폴링용. "
			+ "제한 시간이 지났으면 조회 시점에 TIMED_OUT으로 넘긴다")
	@GetMapping("/{requestId}")
	public ApiResponse<LocationRequestResponse> get(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID requestId) {
		return ApiResponse.success(LocationRequestResponse.from(locationRequestService.get(user, requestId)));
	}
}
