package com.sonulim.domain.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sonulim.domain.link.domain.ConnectionCheck.AckOutcome;

class ConnectionCheckTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 8, 40, 0);

	private final ConnectionCheck check = ConnectionCheck.request(UUID.randomUUID(), UUID.randomUUID(), NOW);

	@Test
	void startsPendingWithTimeoutThirtySecondsLater() {
		assertThat(check.getStatus()).isEqualTo(ConnectionCheckStatus.PENDING);
		assertThat(check.getRequestedAt()).isEqualTo(NOW);
		assertThat(check.getTimeoutAt()).isEqualTo(NOW.plusSeconds(30));
		assertThat(check.getConfirmedAt()).isNull();
	}

	@Test
	void staysPendingJustBeforeTimeout() {
		assertThat(check.resolveIfDue(NOW.plusSeconds(30).minusNanos(1))).isFalse();
		assertThat(check.getStatus()).isEqualTo(ConnectionCheckStatus.PENDING);
	}

	@Test
	void becomesUnconfirmedAtTimeout() {
		assertThat(check.resolveIfDue(NOW.plusSeconds(30))).isTrue();
		assertThat(check.getStatus()).isEqualTo(ConnectionCheckStatus.UNCONFIRMED);
		assertThat(check.resolveIfDue(NOW.plusSeconds(31))).isFalse();
	}

	@Test
	void ackWithinTimeoutConfirms() {
		assertThat(check.ack(NOW.plusSeconds(2))).isEqualTo(AckOutcome.CONFIRMED);
		assertThat(check.getStatus()).isEqualTo(ConnectionCheckStatus.CONFIRMED);
		assertThat(check.getConfirmedAt()).isEqualTo(NOW.plusSeconds(2));
	}

	@Test
	void ackAfterTimeoutIsLateAndLeavesResultUnconfirmed() {
		check.resolveIfDue(NOW.plusSeconds(40));

		assertThat(check.ack(NOW.plusSeconds(40))).isEqualTo(AckOutcome.LATE);
		assertThat(check.getStatus()).isEqualTo(ConnectionCheckStatus.UNCONFIRMED);
		assertThat(check.getConfirmedAt()).isNull();
	}

	@Test
	void onlyFirstAckIsRecorded() {
		check.ack(NOW.plusSeconds(2));

		assertThat(check.ack(NOW.plusSeconds(5))).isEqualTo(AckOutcome.ALREADY_ACKED);
		assertThat(check.getConfirmedAt()).isEqualTo(NOW.plusSeconds(2));
	}

	@Test
	void secondLateAckIsAlsoIgnored() {
		check.resolveIfDue(NOW.plusSeconds(40));
		check.ack(NOW.plusSeconds(40));

		assertThat(check.ack(NOW.plusSeconds(50))).isEqualTo(AckOutcome.ALREADY_ACKED);
	}
}
