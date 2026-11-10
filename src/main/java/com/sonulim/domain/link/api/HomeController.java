package com.sonulim.domain.link.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.link.application.HomeService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "홈")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
public class HomeController {

	private final HomeService homeService;

	public HomeController(HomeService homeService) {
		this.homeService = homeService;
	}

	@Operation(summary = "홈 요약", description = "S-4 · 보호자. 당사자 정보·연결 상태·현재 상태(HELP_REQUESTED > NO_RESPONSE "
			+ "> WAITING > OK)·마지막 응답, 자주 쓰는 신호(최대 6개), 위치(정책 확정 전 false/null), 최근 기록 3건. "
			+ "연결 구성원이 아니면 404, linkId 형식 오류는 400")
	@GetMapping("/api/v1/links/{linkId}/home")
	public ApiResponse<HomeResponse> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID linkId) {
		return ApiResponse.success(HomeResponse.from(homeService.get(user, linkId)));
	}
}
