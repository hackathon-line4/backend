package com.sonulim.domain.history.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.history.application.HistoryService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "기록")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/links/{linkId}/history")
public class HistoryController {

	private final HistoryService historyService;

	public HistoryController(HistoryService historyService) {
		this.historyService = historyService;
	}

	@Operation(summary = "신호 기록 목록", description = "H-1 · 보호자. 고른 날짜(기본 오늘)의 보호자 신호·당사자 신호·도움 요청·연습을 "
			+ "최신순으로 전부. 하루는 tz(기본 Asia/Seoul) 기준. filter=NO_RESPONSE면 응답 없음인 보호자 신호만. "
			+ "오늘보다 뒤 날짜는 400")
	@GetMapping
	public ApiResponse<HistoryResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable String linkId,
			@RequestParam(required = false) String date,
			@RequestParam(required = false) String filter,
			@RequestParam(required = false) String tz) {
		return ApiResponse.success(HistoryResponse.from(historyService.list(user, linkId, date, filter, tz)));
	}

	@Operation(summary = "기록 있는 날짜(달력)", description = "H-1 · 보호자. month(YYYY-MM, 필수)에서 기록이 있는 날만. "
			+ "hasNoResponse는 그날 응답 없음이 하나라도 있으면 true")
	@GetMapping("/calendar")
	public ApiResponse<HistoryCalendarResponse> calendar(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable String linkId,
			@RequestParam(required = false) String month,
			@RequestParam(required = false) String tz) {
		return ApiResponse.success(HistoryCalendarResponse.from(historyService.calendar(user, linkId, month, tz)));
	}
}
