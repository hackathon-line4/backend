package com.sonulim.domain.location.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.location.application.FulfillLocationService;
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
	private final FulfillLocationService fulfillLocationService;

	public LocationRequestController(LocationRequestService locationRequestService,
			FulfillLocationService fulfillLocationService) {
		this.locationRequestService = locationRequestService;
		this.fulfillLocationService = fulfillLocationService;
	}

	@Operation(summary = "위치 보내기 (당사자)", description = "P-1 · 당사자만. 위치 요청 푸시를 받은 앱이 "
			+ "백그라운드에서 위치를 1회 얻어 보낸다. 못 얻었으면 error에 사유를 담는다. "
			+ "위치 공유가 꺼져 있으면 좌표가 와도 저장하지 않고 FAILED/SHARING_OFF. "
			+ "시간 초과된 요청이어도 늦게 온 위치는 마지막 위치에 반영한다")
	@PostMapping("/{requestId}/fulfill")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void fulfill(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID requestId,
			@Valid @RequestBody FulfillLocationRequest request) {
		request.requireComplete();
		if (request.isFailure()) {
			fulfillLocationService.fail(user, requestId, request.error());
			return;
		}
		fulfillLocationService.fulfill(user, requestId, request.latitude(), request.longitude(),
				request.accuracyM(), request.capturedAt());
	}

	@Operation(summary = "새로고침 요청 상태", description = "P-1 · 보호자만. SSE가 끊겼을 때 폴링용. "
			+ "제한 시간이 지났으면 조회 시점에 TIMED_OUT으로 넘긴다")
	@GetMapping("/{requestId}")
	public ApiResponse<LocationRequestResponse> get(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID requestId) {
		return ApiResponse.success(LocationRequestResponse.from(locationRequestService.get(user, requestId)));
	}
}
