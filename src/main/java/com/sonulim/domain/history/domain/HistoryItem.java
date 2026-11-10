package com.sonulim.domain.history.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 기록 목록 한 줄. messages·recipient_signals·help_requests를 같은 모양으로 맞춘 history의 읽기 모델이다 — 다른 도메인의
 * 엔티티를 그대로 들고 다니지 않는다(docs/architecture.md 느슨한 결합).
 *
 * 시각·상태를 고르는 규칙과 summaryStatus·canResend는 명세에 일부만 있어서 해석해 정했다
 * (docs/domains/history.md Pending Decisions "명세 해석").
 */
public record HistoryItem(
		UUID id,
		HistoryKind kind,
		String title,
		String description,
		boolean emergency,
		LocalDateTime occurredAt,
		DeliveryState deliveryStatus,
		LocalDateTime deliveredAt,
		String failureReason,
		ResponseState responseStatus,
		LocalDateTime respondedAt,
		LocalDateTime openedAt) {

	/**
	 * 보호자가 보낸 신호·연습 한 건.
	 * <ul>
	 *   <li>delivery.at: DELIVERED → 전달 시각, FAILED → 요청 시각(명세 예시), UNCONFIRMED → 미확인 판정 시각, REQUESTED → null</li>
	 *   <li>response.at: RESPONDED → 응답 시각, NO_RESPONSE → 응답 없음 판정 시각(명세 예시), WAITING → null</li>
	 * </ul>
	 */
	public static HistoryItem guardianMessage(UUID id, boolean practice, String title, String description,
			boolean emergency, LocalDateTime requestedAt, DeliveryState delivery, String failureReason,
			LocalDateTime deliveredAt, LocalDateTime unconfirmedAt, ResponseState response,
			LocalDateTime respondedAt, LocalDateTime noResponseAt) {
		LocalDateTime deliveryAt = switch (delivery) {
			case DELIVERED -> deliveredAt;
			case FAILED -> requestedAt;
			case UNCONFIRMED -> unconfirmedAt;
			case REQUESTED -> null;
		};
		LocalDateTime responseAt = response == null ? null : switch (response) {
			case RESPONDED -> respondedAt;
			case NO_RESPONSE -> noResponseAt;
			case WAITING -> null;
		};
		return new HistoryItem(id, practice ? HistoryKind.PRACTICE : HistoryKind.GUARDIAN_SIGNAL, title, description,
				emergency, requestedAt, delivery, deliveryAt, failureReason, response, responseAt, null);
	}

	/**
	 * 당사자가 보낸 신호·도움 요청 한 건. delivery = 보호자 휴대폰 도착(명세) — 도착 시각이 있으면 DELIVERED, 없으면 REQUESTED.
	 * response는 없고 openedAt = 보호자가 열어 본 시각.
	 */
	public static HistoryItem fromRecipient(UUID id, HistoryKind kind, String title, boolean emergency,
			LocalDateTime sentAt, LocalDateTime guardianDeliveredAt, LocalDateTime guardianOpenedAt) {
		DeliveryState delivery = guardianDeliveredAt != null ? DeliveryState.DELIVERED : DeliveryState.REQUESTED;
		return new HistoryItem(id, kind, title, null, emergency, sentAt, delivery, guardianDeliveredAt, null, null,
				null, guardianOpenedAt);
	}

	/**
	 * 전달 REQUESTED → SENDING, FAILED → FAILED, UNCONFIRMED → UNCONFIRMED. DELIVERED면 응답 상태 그대로, 응답이 없으면
	 * (연습·당사자 기록) DELIVERED_ONLY.
	 */
	public SummaryStatus summaryStatus() {
		return switch (deliveryStatus) {
			case REQUESTED -> SummaryStatus.SENDING;
			case FAILED -> SummaryStatus.FAILED;
			case UNCONFIRMED -> SummaryStatus.UNCONFIRMED;
			case DELIVERED -> responseStatus == null ? SummaryStatus.DELIVERED_ONLY : switch (responseStatus) {
				case WAITING -> SummaryStatus.WAITING;
				case RESPONDED -> SummaryStatus.RESPONDED;
				case NO_RESPONSE -> SummaryStatus.NO_RESPONSE;
			};
		};
	}

	/**
	 * 보호자 신호(연습 제외)가 전송 실패·전달 확인 안 됨·응답 없음이면 다시 보낼 수 있다. FAILED·NO_RESPONSE는 명세 예시,
	 * UNCONFIRMED는 추측이다(프론트 확인 필요). 원래 신호가 삭제됐는지는 signal 도메인 구현 후 반영한다.
	 */
	public boolean canResend() {
		return kind == HistoryKind.GUARDIAN_SIGNAL
				&& (deliveryStatus == DeliveryState.FAILED || deliveryStatus == DeliveryState.UNCONFIRMED
						|| responseStatus == ResponseState.NO_RESPONSE);
	}

	/**
	 * filter=NO_RESPONSE와 달력의 hasNoResponse 기준: 터치 응답이 NO_RESPONSE인 보호자 신호(명세).
	 */
	public boolean isNoResponse() {
		return kind == HistoryKind.GUARDIAN_SIGNAL && responseStatus == ResponseState.NO_RESPONSE;
	}
}
