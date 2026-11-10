package com.sonulim.domain.recipient.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.recipient.application.HelpRequestQueryService;
import com.sonulim.domain.recipient.application.RecipientReceiptService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "당사자")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1")
public class RecipientReceiptController {

	private final RecipientReceiptService recipientReceiptService;
	private final HelpRequestQueryService helpRequestQueryService;

	public RecipientReceiptController(RecipientReceiptService recipientReceiptService,
			HelpRequestQueryService helpRequestQueryService) {
		this.recipientReceiptService = recipientReceiptService;
		this.helpRequestQueryService = helpRequestQueryService;
	}

	@Operation(summary = "도움 요청 상세", description = "H-3 · 보호자만. 당사자가 보냄 → 내 휴대폰에 도착 → "
			+ "내가 열어 봄 시각과 위치, 요청 이후 주고받은 신호를 보여 준다. "
			+ "열람은 여기서 기록하지 않는다 — 화면을 열 때 도착·열람 기록 API를 따로 부른다")
	@GetMapping("/help-requests/{id}")
	public ApiResponse<HelpRequestDetailResponse> helpRequestDetail(
			@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return ApiResponse.success(HelpRequestDetailResponse.from(helpRequestQueryService.detail(user, id)));
	}

	@Operation(summary = "당사자 신호 도착·열람 기록", description = "R-2 · 보호자만. "
			+ "DELIVERED는 푸시 수신 핸들러에서, OPENED는 알림이나 기록을 눌러 열었을 때 보낸다. "
			+ "각각 처음 시각만 기록하고, 열람이 먼저 오면 도착도 같은 시각으로 채운다")
	@PostMapping("/recipient-signals/{id}/receipts")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void recordSignalReceipt(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id, @Valid @RequestBody ReceiptRequest request) {
		if (request.type() == ReceiptType.OPENED) {
			recipientReceiptService.recordSignalOpened(user, id);
		}
		else {
			recipientReceiptService.recordSignalDelivered(user, id);
		}
	}

	@Operation(summary = "도움 요청 도착·열람 기록", description = "R-3, H-3 · 보호자만. "
			+ "상세 조회와 따로 부른다 — 화면을 열 때 OPENED를 보낸다. "
			+ "첫 열람에서 재알림이 멈추고 당사자에게 '확인함'이 전달된다")
	@PostMapping("/help-requests/{id}/receipts")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void recordHelpRequestReceipt(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id, @Valid @RequestBody ReceiptRequest request) {
		if (request.type() == ReceiptType.OPENED) {
			recipientReceiptService.recordHelpRequestOpened(user, id);
		}
		else {
			recipientReceiptService.recordHelpRequestDelivered(user, id);
		}
	}
}
