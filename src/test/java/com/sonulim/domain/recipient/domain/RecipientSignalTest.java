package com.sonulim.domain.recipient.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecipientSignalTest {

	private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 11, 4, 15, 20, 5);

	private static RecipientSignal sent() {
		return RecipientSignal.send(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				RecipientSignalType.CALL_GUARDIAN, SENT_AT);
	}

	@Test
	void aFreshCallHasNotReachedTheGuardianYet() {
		RecipientSignal signal = sent();

		assertThat(signal.getSentAt()).isEqualTo(SENT_AT);
		// 당사자 응답에는 보호자 도착·열람 여부를 넣지 않는다
		assertThat(signal.getGuardianDeliveredAt()).isNull();
		assertThat(signal.getGuardianOpenedAt()).isNull();
	}

	@Test
	void deliveryIsRecordedOnlyOnce() {
		RecipientSignal signal = sent();
		LocalDateTime first = SENT_AT.plusSeconds(1);

		signal.markDelivered(first);
		signal.markDelivered(SENT_AT.plusSeconds(30));

		assertThat(signal.getGuardianDeliveredAt()).isEqualTo(first);
	}

	@Test
	void openingWithoutADeliveryReportFillsBothAtTheSameMoment() {
		RecipientSignal signal = sent();
		LocalDateTime opened = SENT_AT.plusSeconds(40);

		signal.markOpened(opened);

		assertThat(signal.getGuardianOpenedAt()).isEqualTo(opened);
		assertThat(signal.getGuardianDeliveredAt()).isEqualTo(opened);
	}

	@Test
	void openingTwiceKeepsTheFirstTime() {
		RecipientSignal signal = sent();
		LocalDateTime first = SENT_AT.plusSeconds(40);

		signal.markOpened(first);
		signal.markOpened(SENT_AT.plusMinutes(5));

		assertThat(signal.getGuardianOpenedAt()).isEqualTo(first);
	}
}
