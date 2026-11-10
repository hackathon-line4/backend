package com.sonulim.domain.messaging.api;

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

import com.sonulim.domain.messaging.application.MessageQueryService;
import com.sonulim.domain.messaging.application.SendMessageService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.idempotency.IdempotencyService;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "전송")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1")
public class SendMessageController {

	private final SendMessageService sendMessageService;
	private final IdempotencyService idempotencyService;

	public SendMessageController(SendMessageService sendMessageService, IdempotencyService idempotencyService) {
		this.sendMessageService = sendMessageService;
		this.idempotencyService = idempotencyService;
	}

	@Operation(summary = "신호 보내기", description = "S-1, S-2 · 보호자만. 일반 신호와 긴급 신호가 같은 API다. "
			+ "기록을 만들고 당사자 기기에 보낸 뒤 ack를 기다린다 — 전달 완료는 ack로만 바뀐다. "
			+ "당사자 기기가 없으면 오류가 아니라 201에 FAILED로 담아 돌려준다. "
			+ "같은 Idempotency-Key로 재시도하면 신호가 두 번 울리지 않는다")
	@PostMapping("/links/{linkId}/messages")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MessageResponse> send(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID linkId,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody SendMessageRequest request) {
		return ApiResponse.success(idempotencyService.execute(user.userId(), idempotencyKey, request,
				MessageResponse.class, () -> toResponse(sendMessageService.send(user, linkId, request.signalId()))));
	}

	@Operation(summary = "다시 보내기", description = "H-1, H-2, S-3 · 보호자만. 같은 신호로 새 기록을 만든다. "
			+ "신호의 현재 값으로 스냅샷을 다시 뜨고 원래 기록은 바뀌지 않는다. "
			+ "원래 신호가 삭제됐으면 409 SIGNAL_DELETED")
	@PostMapping("/messages/{messageId}/resend")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MessageResponse> resend(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID messageId,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
		return ApiResponse.success(idempotencyService.execute(user.userId(), idempotencyKey, messageId,
				MessageResponse.class, () -> toResponse(sendMessageService.resend(user, messageId))));
	}

	private static MessageResponse toResponse(MessageQueryService.MessageDetail detail) {
		return MessageResponse.of(detail.message(), detail.sender(), detail.receiver());
	}
}
