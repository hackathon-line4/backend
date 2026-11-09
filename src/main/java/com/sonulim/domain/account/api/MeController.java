package com.sonulim.domain.account.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.account.application.DeviceService;
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
	private final DeviceService deviceService;

	public MeController(MeService meService, DeviceService deviceService) {
		this.meService = meService;
		this.deviceService = deviceService;
	}

	@Operation(summary = "내 정보 조회", description = "A-2 · 공통(역할 없어도 호출 가능). 자동 로그인·앱 시작 화면 분기용")
	@GetMapping
	public ApiResponse<MeResponse> getMe(@AuthenticationPrincipal AuthenticatedUser user) {
		return ApiResponse.success(MeResponse.from(meService.getMe(user.userId())));
	}

	@Operation(summary = "역할 선택", description = "A-3 · 공통(역할 없어도 호출 가능). 같은 역할 재요청은 200, 다른 역할은 "
			+ "409 ROLE_ALREADY_SET. 역할이 담긴 새 액세스·갱신 토큰을 함께 반환")
	@PutMapping("/role")
	public ApiResponse<RoleSelectionResponse> selectRole(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody RoleRequest request) {
		return ApiResponse.success(RoleSelectionResponse.from(meService.selectRole(user.userId(), request.toRole())));
	}

	@Operation(summary = "기기·푸시 토큰 등록", description = "A-2, A-4 · 공통. 로그인 직후·앱 시작·푸시 토큰 변경 때 호출. "
			+ "같은 푸시 토큰의 다른 기기와 이 사용자의 이전 기기는 지운다(사용자당 1대)")
	@PutMapping("/devices/{installationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void registerDevice(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String installationId,
			@Valid @RequestBody DeviceRequest request) {
		deviceService.register(user.userId(), installationId, request.toRegistration());
	}
}
