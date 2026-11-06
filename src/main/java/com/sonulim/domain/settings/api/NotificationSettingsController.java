package com.sonulim.domain.settings.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.settings.application.NotificationSettingsService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "설정")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/me/notification-settings")
public class NotificationSettingsController {

	private final NotificationSettingsService notificationSettingsService;

	public NotificationSettingsController(NotificationSettingsService notificationSettingsService) {
		this.notificationSettingsService = notificationSettingsService;
	}

	@Operation(summary = "알림 설정 조회", description = "T-1 · 보호자만. 도움 요청 알림은 항상 켜짐(true)")
	@GetMapping
	public ApiResponse<NotificationSettingsResponse> get(@AuthenticationPrincipal AuthenticatedUser user) {
		return ApiResponse.success(NotificationSettingsResponse.from(notificationSettingsService.get(user)));
	}

	@Operation(summary = "알림 설정 변경", description = "T-1 · 보호자만. 보낸 필드만 바꾼다. responseWaitMinutes는 5·10·30, "
			+ "helpRequestAlert를 보내면 400 FIELD_READ_ONLY")
	@PatchMapping
	public ApiResponse<NotificationSettingsResponse> update(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestBody NotificationSettingsUpdateRequest request) {
		return ApiResponse.success(
				NotificationSettingsResponse.from(notificationSettingsService.update(user, request.toChange())));
	}
}
