package com.sonulim.domain.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sonulim.domain.history.domain.DeliveryState;
import com.sonulim.domain.history.domain.HistoryItem;
import com.sonulim.domain.history.domain.HistoryKind;
import com.sonulim.domain.history.domain.ResponseState;

/**
 * 우선순위 HELP_REQUESTED > NO_RESPONSE > WAITING > OK와 since·elapsedMinutes(명세 해석, Pending Decisions).
 */
class RecipientStatusTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 3, 0, 0);

	@Test
	void unopenedHelpRequestWinsOverNoResponseSignal() {
		HistoryItem help = helpRequest(NOW.minusMinutes(5));
		HistoryItem signal = signal(DeliveryState.DELIVERED, ResponseState.NO_RESPONSE, NOW.minusMinutes(20));

		RecipientStatus status = RecipientStatus.decide(help, signal, NOW);

		assertThat(status.type()).isEqualTo(RecipientStatus.Type.HELP_REQUESTED);
		assertThat(status.messageId()).isEqualTo(help.id());
		assertThat(status.signalName()).isEqualTo("도움 요청");
		assertThat(status.since()).isEqualTo(NOW.minusMinutes(5));
		assertThat(status.elapsedMinutes()).isEqualTo(5);
	}

	@Test
	void latestSignalWithoutResponseIsNoResponse() {
		HistoryItem signal = signal(DeliveryState.DELIVERED, ResponseState.NO_RESPONSE, NOW.minusMinutes(20));

		RecipientStatus status = RecipientStatus.decide(null, signal, NOW);

		assertThat(status.type()).isEqualTo(RecipientStatus.Type.NO_RESPONSE);
		assertThat(status.messageId()).isEqualTo(signal.id());
		assertThat(status.signalName()).isEqualTo("약 먹을 시간");
		assertThat(status.elapsedMinutes()).isEqualTo(20);
	}

	// 분은 내림한다: 2분 59초 → 2.
	@Test
	void waitingSignalCountsWholeMinutes() {
		HistoryItem signal = signal(DeliveryState.DELIVERED, ResponseState.WAITING, NOW.minusSeconds(179));

		RecipientStatus status = RecipientStatus.decide(null, signal, NOW);

		assertThat(status.type()).isEqualTo(RecipientStatus.Type.WAITING);
		assertThat(status.elapsedMinutes()).isEqualTo(2);
	}

	@Test
	void respondedSignalIsOk() {
		assertThat(RecipientStatus.decide(null, signal(DeliveryState.DELIVERED, ResponseState.RESPONDED,
				NOW.minusMinutes(3)), NOW)).isEqualTo(RecipientStatus.OK);
	}

	// 명세 값 목록에 없는 전달 중·실패·미확인은 OK(명세 해석).
	@Test
	void undeliveredSignalsAreOk() {
		assertThat(RecipientStatus.decide(null, signal(DeliveryState.REQUESTED, null, NOW), NOW))
				.isEqualTo(RecipientStatus.OK);
		assertThat(RecipientStatus.decide(null, signal(DeliveryState.FAILED, null, NOW), NOW))
				.isEqualTo(RecipientStatus.OK);
		assertThat(RecipientStatus.decide(null, signal(DeliveryState.UNCONFIRMED, null, NOW), NOW))
				.isEqualTo(RecipientStatus.OK);
	}

	@Test
	void noRecordsIsOkWithEmptyFields() {
		RecipientStatus status = RecipientStatus.decide(null, null, NOW);

		assertThat(status.type()).isEqualTo(RecipientStatus.Type.OK);
		assertThat(status.messageId()).isNull();
		assertThat(status.signalName()).isNull();
		assertThat(status.since()).isNull();
		assertThat(status.elapsedMinutes()).isNull();
	}

	private static HistoryItem signal(DeliveryState delivery, ResponseState response, LocalDateTime requestedAt) {
		return HistoryItem.guardianMessage(UUID.randomUUID(), false, "약 먹을 시간", null, false, requestedAt, delivery,
				null, requestedAt.plusSeconds(2), null, response, null, null);
	}

	private static HistoryItem helpRequest(LocalDateTime sentAt) {
		return HistoryItem.fromRecipient(UUID.randomUUID(), HistoryKind.HELP_REQUEST, "도움 요청", true, sentAt,
				sentAt.plusSeconds(1), null);
	}
}
