package com.sonulim.domain.recipient.api;

import java.time.LocalDateTime;
import java.util.UUID;

import com.sonulim.domain.account.application.UserSummary;
import com.sonulim.domain.recipient.domain.RecipientSignal;
import com.sonulim.domain.recipient.domain.RecipientSignalType;

/**
 * 당사자가 보낸 신호 한 건(공통 데이터 형식).
 * 당사자 응답에는 보호자 도착·열람 여부가 들어가지 않는다 — 생성 시점에는 항상 null이다.
 */
public record RecipientSignalResponse(UUID id, UUID linkId, RecipientSignalType type, Sender sender,
		LocalDateTime sentAt, LocalDateTime guardianDeliveredAt, LocalDateTime guardianOpenedAt) {

	public record Sender(UUID id, String name) {
	}

	public static RecipientSignalResponse of(RecipientSignal signal, UserSummary sender) {
		return new RecipientSignalResponse(signal.getId(), signal.getLinkId(), signal.getType(),
				new Sender(signal.getSenderId(), sender == null ? null : sender.name()),
				signal.getSentAt(), signal.getGuardianDeliveredAt(), signal.getGuardianOpenedAt());
	}
}
