package com.sonulim.domain.messaging.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.sonulim.domain.account.application.UserSummary;
import com.sonulim.domain.messaging.domain.DeliveryFailureReason;
import com.sonulim.domain.messaging.domain.DeliveryStatus;
import com.sonulim.domain.messaging.domain.Message;
import com.sonulim.domain.messaging.domain.MessageKind;
import com.sonulim.domain.messaging.domain.ResponseStatus;
import com.sonulim.domain.messaging.domain.SignalCategory;

/**
 * 보호자가 보낸 신호 한 건(공통 데이터 형식). signal은 보낸 시점 스냅샷이라 신호를 고치거나 지워도 바뀌지 않는다.
 */
public record MessageResponse(UUID id, MessageKind kind, UUID linkId, boolean isEmergency, Signal signal,
		Party sender, Party receiver, Delivery delivery, Playback playback, Response response,
		boolean emergencyWarning, UUID resendOf, UUID relatedHelpRequestId, LocalDateTime createdAt) {

	public record Signal(UUID id, String name, String description, SignalCategory category, String icon,
			List<String> rhythm, List<Integer> pulses, int durationMs) {
	}

	public record Party(UUID id, String name) {
	}

	public record Delivery(DeliveryStatus status, DeliveryFailureReason failureReason, LocalDateTime requestedAt,
			LocalDateTime deliveredAt, LocalDateTime unconfirmedAt) {
	}

	public record Playback(int count, LocalDateTime lastReportedAt) {
	}

	public record Response(ResponseStatus status, LocalDateTime waitUntil, LocalDateTime respondedAt,
			LocalDateTime noResponseAt, Long secondsToRespond, boolean late) {
	}

	public static MessageResponse of(Message message, UserSummary sender, UserSummary receiver) {
		var snapshot = message.getSignalSnapshot();
		return new MessageResponse(message.getId(), message.getKind(), message.getLinkId(), message.isEmergency(),
				new Signal(snapshot.signalId(), snapshot.name(), snapshot.description(), snapshot.category(),
						snapshot.icon(), snapshot.rhythm(), snapshot.pulses(), snapshot.durationMs()),
				party(message.getGuardianId(), sender), party(message.getRecipientId(), receiver),
				new Delivery(message.getDeliveryStatus(), message.getFailureReason(), message.getRequestedAt(),
						message.getDeliveredAt(), message.getUnconfirmedAt()),
				new Playback(message.getPlayCount(), message.getPlaybackReportedAt()),
				new Response(message.getResponseStatus(), message.getWaitUntil(), message.getRespondedAt(),
						message.getNoResponseAt(), message.getSecondsToRespond(), message.isLate()),
				message.isEmergencyWarning(), message.getResendOfId(), message.getRelatedHelpRequestId(),
				message.getCreatedAt());
	}

	/** 탈퇴 등으로 사용자가 없으면 이름만 비우고 기록 자체는 보여 준다. */
	private static Party party(UUID userId, UserSummary user) {
		return new Party(userId, user == null ? null : user.name());
	}
}
