package com.sonulim.domain.messaging.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.messaging.application.MessageQueryService;
import com.sonulim.domain.messaging.application.MessageWaitService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "전송")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

	private final MessageQueryService messageQueryService;
	private final MessageWaitService messageWaitService;

	public MessageController(MessageQueryService messageQueryService, MessageWaitService messageWaitService) {
		this.messageQueryService = messageQueryService;
		this.messageWaitService = messageWaitService;
	}

	@Operation(summary = "받은 신호 조회", description = "R-1 · 당사자만. 푸시를 놓쳤거나 앱을 다시 열었을 때 "
			+ "지금 울려야 하는 신호를 가져온다. 응답을 기다리는 중이거나 아직 ack하지 않은 것을 오래된 순으로 준다. "
			+ "playbackPolicy의 null은 앱 기본값을 쓰라는 뜻이고, 긴급 신호만 값이 채워진다")
	@GetMapping("/incoming")
	public ApiResponse<IncomingMessagesResponse> incoming(@AuthenticationPrincipal AuthenticatedUser user) {
		MessageQueryService.IncomingMessages incoming = messageQueryService.incoming(user);
		return ApiResponse.success(new IncomingMessagesResponse(incoming.messages().stream()
				.map(message -> IncomingMessageResponse.of(message,
						name(incoming, message.getGuardianId()), incoming.emergencyPlayback()))
				.toList()));
	}

	@Operation(summary = "신호 기록 상세", description = "H-2, S-3 · 보호자만. 기기 전달과 터치 응답을 "
			+ "초 단위 시각과 함께 보여 준다. 응답 없음 상세도 이 API다. "
			+ "relatedHelpRequest는 기준 시간이 미확정이라 아직 항상 null이다")
	@GetMapping("/{messageId}")
	public ApiResponse<MessageDetailResponse> detail(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID messageId) {
		MessageQueryService.MessageDetail detail = messageQueryService.detail(user, messageId);
		return ApiResponse.success(MessageDetailResponse.of(
				MessageResponse.of(detail.message(), detail.sender(), detail.receiver()),
				detail.message().getTimeline(), detail.message().canExtendWait(), detail.canResend()));
	}

	@Operation(summary = "더 기다리기", description = "S-3 · 보호자만. 응답 대기 시간을 늘려 늦은 응답을 받는다. "
			+ "minutes를 생략하면 보호자 설정값. 이미 응답이 왔으면 409 MESSAGE_NOT_WAITING. "
			+ "당사자 기기를 다시 울리지는 않는다")
	@PostMapping("/{messageId}/extend-wait")
	public ApiResponse<MessageResponse> extendWait(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID messageId, @Valid @RequestBody(required = false) ExtendWaitRequest request) {
		MessageQueryService.MessageDetail extended = messageWaitService.extendWait(user, messageId,
				request == null ? null : request.minutes());
		return ApiResponse.success(MessageResponse.of(extended.message(), extended.sender(), extended.receiver()));
	}

	private static String name(MessageQueryService.IncomingMessages incoming, UUID guardianId) {
		var guardian = incoming.guardians().get(guardianId);
		return guardian == null ? null : guardian.name();
	}
}
