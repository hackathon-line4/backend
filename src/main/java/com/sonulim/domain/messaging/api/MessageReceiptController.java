package com.sonulim.domain.messaging.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.messaging.application.MessageReceiptService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "수신")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/messages/{messageId}")
public class MessageReceiptController {

	private final MessageReceiptService messageReceiptService;

	public MessageReceiptController(MessageReceiptService messageReceiptService) {
		this.messageReceiptService = messageReceiptService;
	}

	@Operation(summary = "수신 확인 (ack)", description = "S-1, R-1 · 당사자만(받는 사람). "
			+ "이때만 기기 전달 완료가 된다 — 푸시 발송 성공만으로는 바뀌지 않는다. "
			+ "진동을 울리기 전에 먼저 보내고, 응답의 waitUntil까지 반복 진동을 유지한다. 여러 번 보내도 처음 값이 유지된다")
	@PostMapping("/ack")
	public ApiResponse<AckResponse> ack(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID messageId, @Valid @RequestBody AckRequest request) {
		return ApiResponse.success(AckResponse.from(
				messageReceiptService.ack(user, messageId, request.receivedAt())));
	}

	@Operation(summary = "진동 재생 보고", description = "S-1, R-1 · 당사자만(받는 사람). "
			+ "누적 횟수를 보낸다 — 지금 값보다 큰 값만 반영되므로 보고가 순서 뒤바뀌어 도착해도 안전하다. "
			+ "인지했다는 뜻이 아니라 몇 번 울렸는지만 기록한다")
	@PostMapping("/playback")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reportPlayback(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID messageId, @Valid @RequestBody PlaybackRequest request) {
		messageReceiptService.reportPlayback(user, messageId, request.count());
	}

	@Operation(summary = "터치 응답", description = "R-1 · 당사자만(받는 사람). "
			+ "'읽음'이나 '이해함'이 아니라 터치가 있었다는 사실만 기록한다. "
			+ "ack 없이 먼저 와도 ack를 같은 시각으로 함께 기록하고, 응답 없음 판정 뒤에 오면 late로 남긴다. "
			+ "연습 신호에는 409 MESSAGE_NOT_RESPONDABLE")
	@PostMapping("/response")
	public ApiResponse<TouchResponse> respond(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID messageId, @Valid @RequestBody TouchRequest request) {
		return ApiResponse.success(TouchResponse.from(messageReceiptService.respond(user, messageId)));
	}
}
