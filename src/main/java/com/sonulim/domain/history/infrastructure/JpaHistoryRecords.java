package com.sonulim.domain.history.infrastructure;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;

import com.sonulim.domain.history.domain.DeliveryState;
import com.sonulim.domain.history.domain.HistoryItem;
import com.sonulim.domain.history.domain.HistoryKind;
import com.sonulim.domain.history.domain.HistoryRecords;
import com.sonulim.domain.history.domain.ResponseState;
import com.sonulim.domain.messaging.domain.DeliveryStatus;
import com.sonulim.domain.messaging.domain.Message;
import com.sonulim.domain.messaging.domain.MessageKind;
import com.sonulim.domain.messaging.domain.ResponseStatus;
import com.sonulim.domain.recipient.domain.HelpRequest;
import com.sonulim.domain.recipient.domain.RecipientSignal;
import com.sonulim.domain.recipient.domain.RecipientSignalType;

/**
 * history가 messaging·recipient를 읽는 유일한 곳. 그 도메인 코드는 고치지 않고 엔티티를 JPQL로 읽기만 한다 — 그쪽 Repository에
 * 기록 조회 메서드가 없어서다. 그 도메인이 조회 서비스를 내놓으면 이 클래스만 바꾼다(docs/architecture.md 느슨한 결합).
 *
 * 상태 enum은 switch로 하나씩 옮긴다 — 그쪽에 값이 늘면 이름이 우연히 맞아 넘어가지 않고 여기서 컴파일 오류가 난다.
 * Message의 타임라인(LAZY)은 읽지 않는다.
 */
@Repository
class JpaHistoryRecords implements HistoryRecords {

	private final EntityManager entityManager;

	JpaHistoryRecords(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public List<HistoryItem> findByGuardian(UUID linkId, UUID guardianId, LocalDateTime from, LocalDateTime to) {
		List<HistoryItem> items = new ArrayList<>();
		entityManager.createQuery("select m from Message m where m.linkId = :linkId and m.guardianId = :guardianId "
						+ "and m.requestedAt >= :from and m.requestedAt < :to", Message.class)
				.setParameter("linkId", linkId).setParameter("guardianId", guardianId)
				.setParameter("from", from).setParameter("to", to)
				.getResultList()
				.forEach(message -> items.add(toItem(message)));
		entityManager.createQuery("select s from RecipientSignal s where s.linkId = :linkId "
						+ "and s.guardianId = :guardianId and s.sentAt >= :from and s.sentAt < :to", RecipientSignal.class)
				.setParameter("linkId", linkId).setParameter("guardianId", guardianId)
				.setParameter("from", from).setParameter("to", to)
				.getResultList()
				.forEach(signal -> items.add(toItem(signal)));
		entityManager.createQuery("select h from HelpRequest h where h.linkId = :linkId "
						+ "and h.guardianId = :guardianId and h.sentAt >= :from and h.sentAt < :to", HelpRequest.class)
				.setParameter("linkId", linkId).setParameter("guardianId", guardianId)
				.setParameter("from", from).setParameter("to", to)
				.getResultList()
				.forEach(helpRequest -> items.add(toItem(helpRequest)));
		return items;
	}

	@Override
	public boolean existsBefore(UUID linkId, UUID guardianId, LocalDateTime before) {
		return exists("select m.id from Message m where m.linkId = :linkId and m.guardianId = :guardianId "
				+ "and m.requestedAt < :before", linkId, guardianId, before)
				|| exists("select s.id from RecipientSignal s where s.linkId = :linkId and s.guardianId = :guardianId "
						+ "and s.sentAt < :before", linkId, guardianId, before)
				|| exists("select h.id from HelpRequest h where h.linkId = :linkId and h.guardianId = :guardianId "
						+ "and h.sentAt < :before", linkId, guardianId, before);
	}

	private boolean exists(String jpql, UUID linkId, UUID guardianId, LocalDateTime before) {
		return !entityManager.createQuery(jpql, UUID.class)
				.setParameter("linkId", linkId).setParameter("guardianId", guardianId)
				.setParameter("before", before)
				.setMaxResults(1)
				.getResultList()
				.isEmpty();
	}

	private static HistoryItem toItem(Message message) {
		return HistoryItem.guardianMessage(message.getId(), message.getKind() == MessageKind.PRACTICE,
				message.getSignalSnapshot().name(), message.getSignalSnapshot().description(), message.isEmergency(),
				message.getRequestedAt(), delivery(message.getDeliveryStatus()),
				message.getFailureReason() == null ? null : message.getFailureReason().name(),
				message.getDeliveredAt(), message.getUnconfirmedAt(), response(message.getResponseStatus()),
				message.getRespondedAt(), message.getNoResponseAt());
	}

	// 제목은 명세 예시("보호자 부르기"·"도움 요청"), 도움 요청은 isEmergency: true(명세 예시).
	private static HistoryItem toItem(RecipientSignal signal) {
		return HistoryItem.fromRecipient(signal.getId(), HistoryKind.RECIPIENT_SIGNAL, title(signal.getType()), false,
				signal.getSentAt(), signal.getGuardianDeliveredAt(), signal.getGuardianOpenedAt());
	}

	private static HistoryItem toItem(HelpRequest helpRequest) {
		return HistoryItem.fromRecipient(helpRequest.getId(), HistoryKind.HELP_REQUEST, "도움 요청", true,
				helpRequest.getSentAt(), helpRequest.getGuardianDeliveredAt(), helpRequest.getGuardianOpenedAt());
	}

	private static String title(RecipientSignalType type) {
		return switch (type) {
			case CALL_GUARDIAN -> "보호자 부르기";
		};
	}

	private static DeliveryState delivery(DeliveryStatus status) {
		return switch (status) {
			case REQUESTED -> DeliveryState.REQUESTED;
			case FAILED -> DeliveryState.FAILED;
			case DELIVERED -> DeliveryState.DELIVERED;
			case UNCONFIRMED -> DeliveryState.UNCONFIRMED;
		};
	}

	private static ResponseState response(ResponseStatus status) {
		if (status == null) {
			return null;
		}
		return switch (status) {
			case WAITING -> ResponseState.WAITING;
			case RESPONDED -> ResponseState.RESPONDED;
			case NO_RESPONSE -> ResponseState.NO_RESPONSE;
		};
	}
}
