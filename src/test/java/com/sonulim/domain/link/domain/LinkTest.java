package com.sonulim.domain.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LinkTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 1, 12, 30);

	private final UUID guardianId = UUID.randomUUID();
	private final UUID recipientId = UUID.randomUUID();
	private final Link link = Link.connect(guardianId, recipientId, NOW);

	@Test
	void startsActiveWithOneToOneKeys() {
		assertThat(link.getStatus()).isEqualTo(LinkStatus.ACTIVE);
		assertThat(link.getLinkedAt()).isEqualTo(NOW);
		assertThat(link.getEndedAt()).isNull();
		assertThat(ReflectionTestUtils.getField(link, "activeGuardianId")).isEqualTo(guardianId);
		assertThat(ReflectionTestUtils.getField(link, "activeRecipientId")).isEqualTo(recipientId);
	}

	// 1:1 유니크 컬럼을 비워야 두 사람 모두 다시 연결할 수 있다.
	@Test
	void endingRecordsTimeAndClearsOneToOneKeys() {
		link.end(NOW.plusDays(1));

		assertThat(link.getStatus()).isEqualTo(LinkStatus.ENDED);
		assertThat(link.getEndedAt()).isEqualTo(NOW.plusDays(1));
		assertThat(ReflectionTestUtils.getField(link, "activeGuardianId")).isNull();
		assertThat(ReflectionTestUtils.getField(link, "activeRecipientId")).isNull();
		assertThat(link.getGuardianId()).isEqualTo(guardianId);
		assertThat(link.getRecipientId()).isEqualTo(recipientId);
	}

	@Test
	void endingTwiceKeepsFirstEndTime() {
		link.end(NOW.plusDays(1));

		link.end(NOW.plusDays(2));

		assertThat(link.getEndedAt()).isEqualTo(NOW.plusDays(1));
	}

	@Test
	void connectionIsUnknownUntilChecked() {
		assertThat(link.getConnectionState()).isEqualTo(ConnectionState.UNKNOWN);
		assertThat(link.getLastConfirmedAt()).isNull();
	}

	// 연결 확인 컬럼이 생기기 전에 만들어진 행은 null — UNKNOWN으로 읽는다.
	@Test
	void missingConnectionStateReadsAsUnknown() {
		ReflectionTestUtils.setField(link, "connectionState", null);

		assertThat(link.getConnectionState()).isEqualTo(ConnectionState.UNKNOWN);
	}

	@Test
	void confirmedConnectionRecordsLastConfirmedAt() {
		link.connectionConfirmed(NOW.plusMinutes(1));

		assertThat(link.getConnectionState()).isEqualTo(ConnectionState.CONFIRMED);
		assertThat(link.getLastConfirmedAt()).isEqualTo(NOW.plusMinutes(1));
	}

	@Test
	void lateAckKeepsUnconfirmedStateButUpdatesLastConfirmedAt() {
		link.connectionUnconfirmed();

		link.connectionSeenAt(NOW.plusMinutes(2));

		assertThat(link.getConnectionState()).isEqualTo(ConnectionState.UNCONFIRMED);
		assertThat(link.getLastConfirmedAt()).isEqualTo(NOW.plusMinutes(2));
	}

	@Test
	void lastConfirmedAtNeverMovesBackwards() {
		link.connectionConfirmed(NOW.plusMinutes(5));

		link.connectionSeenAt(NOW.plusMinutes(3));

		assertThat(link.getLastConfirmedAt()).isEqualTo(NOW.plusMinutes(5));
	}
}
