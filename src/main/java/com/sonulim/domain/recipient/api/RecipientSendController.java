package com.sonulim.domain.recipient.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.recipient.application.RecipientSendService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.idempotency.IdempotencyService;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "당사자")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/links/{linkId}")
public class RecipientSendController {

	private final RecipientSendService recipientSendService;
	private final IdempotencyService idempotencyService;

	public RecipientSendController(RecipientSendService recipientSendService,
			IdempotencyService idempotencyService) {
		this.recipientSendService = recipientSendService;
		this.idempotencyService = idempotencyService;
	}

	@Operation(summary = "보호자 부르기", description = "R-2 · 당사자만. 보낸 시각은 서버가 받은 시각으로 기록한다. "
			+ "응답에는 보호자 도착·열람 여부가 들어가지 않는다(항상 null) — 그건 보호자 앱이 따로 보고한다")
	@PostMapping("/recipient-signals")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<RecipientSignalResponse> callGuardian(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID linkId,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody CallGuardianRequest request) {
		return ApiResponse.success(idempotencyService.execute(user.userId(), idempotencyKey, request,
				RecipientSignalResponse.class, () -> {
					var called = recipientSendService.callGuardian(user, linkId, request.type());
					return RecipientSignalResponse.of(called.signal(), called.sender());
				}));
	}

	@Operation(summary = "도움 요청", description = "R-3 · 당사자만. 보호자에게 알림 설정과 무관하게 항상 전달된다. "
			+ "위치를 아직 못 얻었으면 기다리지 말고 PENDING으로 먼저 보내고 나중에 붙인다. "
			+ "위치 공유가 꺼져 있으면 좌표가 와도 저장하지 않고 NOT_SHARED로 기록한다")
	@PostMapping("/help-requests")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<HelpRequestResponse> requestHelp(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID linkId,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody CreateHelpRequestRequest request) {
		return ApiResponse.success(idempotencyService.execute(user.userId(), idempotencyKey, request,
				HelpRequestResponse.class, () -> {
					var detail = recipientSendService.requestHelp(user, linkId, request.locationStatus(),
							request.toCapturedLocation());
					return HelpRequestResponse.of(detail.helpRequest(), detail.sender());
				}));
	}
}
