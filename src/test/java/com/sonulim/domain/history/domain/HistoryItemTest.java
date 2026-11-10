package com.sonulim.domain.history.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * 상태·시각 매핑과 summaryStatus·canResend. 명세 예시에 없는 부분은 명세 해석이다(docs/domains/history.md Pending Decisions).
 */
class HistoryItemTest {

	private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 11, 4, 9, 2, 1);
	private static final LocalDateTime DELIVERED_AT = REQUESTED_AT.plusSeconds(2);
	private static final LocalDateTime UNCONFIRMED_AT = REQUESTED_AT.plusSeconds(60);
	private static final LocalDateTime RESPONDED_AT = REQUESTED_AT.plusMinutes(3);
	private static final LocalDateTime NO_RESPONSE_AT = REQUESTED_AT.plusMinutes(10);

	@Test
	void deliveredSignalWithNoResponseMatchesSpecExample() {
		HistoryItem item = signal(DeliveryState.DELIVERED, null, ResponseState.NO_RESPONSE);

		assertThat(item.kind()).isEqualTo(HistoryKind.GUARDIAN_SIGNAL);
		assertThat(item.deliveredAt()).isEqualTo(DELIVERED_AT);
		assertThat(item.respondedAt()).isEqualTo(NO_RESPONSE_AT);
		assertThat(item.summaryStatus()).isEqualTo(SummaryStatus.NO_RESPONSE);
		assertThat(item.canResend()).isTrue();
		assertThat(item.isNoResponse()).isTrue();
	}

	// 명세 예시: 전송 실패의 delivery.at은 요청 시각.
	@Test
	void failedSignalUsesRequestTimeAndCanBeResent() {
		HistoryItem item = signal(DeliveryState.FAILED, "RECIPIENT_NO_DEVICE", null);

		assertThat(item.deliveredAt()).isEqualTo(REQUESTED_AT);
		assertThat(item.failureReason()).isEqualTo("RECIPIENT_NO_DEVICE");
		assertThat(item.summaryStatus()).isEqualTo(SummaryStatus.FAILED);
		assertThat(item.canResend()).isTrue();
	}

	// 명세 예시에 없는 추측: 전달 확인 안 됨도 다시 보낼 수 있다(프론트 확인 필요).
	@Test
	void unconfirmedSignalUsesUnconfirmedTimeAndCanBeResent() {
		HistoryItem item = signal(DeliveryState.UNCONFIRMED, null, null);

		assertThat(item.deliveredAt()).isEqualTo(UNCONFIRMED_AT);
		assertThat(item.summaryStatus()).isEqualTo(SummaryStatus.UNCONFIRMED);
		assertThat(item.canResend()).isTrue();
	}

	@Test
	void requestedSignalIsSendingWithoutDeliveryTime() {
		HistoryItem item = signal(DeliveryState.REQUESTED, null, null);

		assertThat(item.deliveredAt()).isNull();
		assertThat(item.summaryStatus()).isEqualTo(SummaryStatus.SENDING);
		assertThat(item.canResend()).isFalse();
	}

	@Test
	void waitingSignalHasNoResponseTime() {
		HistoryItem item = signal(DeliveryState.DELIVERED, null, ResponseState.WAITING);

		assertThat(item.respondedAt()).isNull();
		assertThat(item.summaryStatus()).isEqualTo(SummaryStatus.WAITING);
		assertThat(item.canResend()).isFalse();
	}

	@Test
	void respondedSignalUsesResponseTime() {
		HistoryItem item = signal(DeliveryState.DELIVERED, null, ResponseState.RESPONDED);

		assertThat(item.respondedAt()).isEqualTo(RESPONDED_AT);
		assertThat(item.summaryStatus()).isEqualTo(SummaryStatus.RESPONDED);
		assertThat(item.canResend()).isFalse();
		assertThat(item.isNoResponse()).isFalse();
	}

	@Test
	void deliveredPracticeIsDeliveredOnlyAndNotResendable() {
		HistoryItem item = HistoryItem.guardianMessage(UUID.randomUUID(), true, "약 먹을 시간", null, false,
				REQUESTED_AT, DeliveryState.DELIVERED, null, DELIVERED_AT, null, null, null, null);

		assertThat(item.kind()).isEqualTo(HistoryKind.PRACTICE);
		assertThat(item.summaryStatus()).isEqualTo(SummaryStatus.DELIVERED_ONLY);
		assertThat(item.canResend()).isFalse();
	}

	// 공통 데이터 형식: 연습은 response가 항상 null이라 응답 없음으로 세지 않는다.
	@Test
	void failedPracticeIsNotResendable() {
		HistoryItem item = HistoryItem.guardianMessage(UUID.randomUUID(), true, "약 먹을 시간", null, false,
				REQUESTED_AT, DeliveryState.FAILED, "PUSH_PROVIDER_ERROR", null, null, null, null, null);

		assertThat(item.summaryStatus()).isEqualTo(SummaryStatus.FAILED);
		assertThat(item.canResend()).isFalse();
	}

	@Test
	void recipientRecordArrivedOnGuardianPhoneIsDeliveredOnly() {
		HistoryItem item = HistoryItem.fromRecipient(UUID.randomUUID(), HistoryKind.HELP_REQUEST, "도움 요청", true,
				REQUESTED_AT, DELIVERED_AT, RESPONDED_AT);

		assertThat(item.deliveryStatus()).isEqualTo(DeliveryState.DELIVERED);
		assertThat(item.deliveredAt()).isEqualTo(DELIVERED_AT);
		assertThat(item.openedAt()).isEqualTo(RESPONDED_AT);
		assertThat(item.responseStatus()).isNull();
		assertThat(item.summaryStatus()).isEqualTo(SummaryStatus.DELIVERED_ONLY);
		assertThat(item.canResend()).isFalse();
	}

	@Test
	void recipientRecordNotYetOnGuardianPhoneIsSending() {
		HistoryItem item = HistoryItem.fromRecipient(UUID.randomUUID(), HistoryKind.RECIPIENT_SIGNAL, "보호자 부르기",
				false, REQUESTED_AT, null, null);

		assertThat(item.deliveryStatus()).isEqualTo(DeliveryState.REQUESTED);
		assertThat(item.summaryStatus()).isEqualTo(SummaryStatus.SENDING);
	}

	private static HistoryItem signal(DeliveryState delivery, String failureReason, ResponseState response) {
		return HistoryItem.guardianMessage(UUID.randomUUID(), false, "약 먹을 시간", "저녁 약을 먹자는 뜻", false,
				REQUESTED_AT, delivery, failureReason, DELIVERED_AT, UNCONFIRMED_AT, response, RESPONDED_AT,
				NO_RESPONSE_AT);
	}
}
