package com.sonulim.domain.recipient.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.sonulim.domain.messaging.application.MessageQueryService;
import com.sonulim.domain.recipient.application.HelpRequestQueryService;

/**
 * 도움 요청 상세(H-3).
 *
 * @param followingMessages 요청 이후 보호자가 보낸 신호. 다음 도움 요청 전까지 최대 20건, 시간순
 */
public record HelpRequestDetailResponse(HelpRequestResponse helpRequest,
		List<FollowingMessage> followingMessages) {

	public record FollowingMessage(UUID id, String signalName, LocalDateTime sentAt, LocalDateTime deliveredAt,
			LocalDateTime respondedAt) {

		static FollowingMessage from(MessageQueryService.SentSignal signal) {
			return new FollowingMessage(signal.id(), signal.signalName(), signal.sentAt(), signal.deliveredAt(),
					signal.respondedAt());
		}
	}

	static HelpRequestDetailResponse from(HelpRequestQueryService.HelpRequestDetail detail) {
		return new HelpRequestDetailResponse(
				HelpRequestResponse.of(detail.helpRequest(), detail.sender()),
				detail.followingMessages().stream().map(FollowingMessage::from).toList());
	}
}
