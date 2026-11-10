package com.sonulim.global.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidFactory;
import com.sonulim.global.realtime.RecentEventBuffer.StoredEvent;

class RecentEventBufferTest {

	private static final Instant NOW = Instant.parse("2026-11-08T03:00:00Z");

	private final RecentEventBuffer buffer = new RecentEventBuffer();
	private final UlidFactory ulidFactory = UlidFactory.newInstance();
	private final UUID userId = UUID.randomUUID();

	@Test
	void returnsEventsAfterLastEventIdInOrder() {
		StoredEvent first = storeAt(NOW.minusSeconds(30));
		StoredEvent second = storeAt(NOW.minusSeconds(20));
		StoredEvent third = storeAt(NOW.minusSeconds(10));

		assertThat(buffer.after(userId, first.id().toString(), NOW)).containsExactly(second, third);
	}

	@Test
	void doesNotReturnOtherUsersEvents() {
		StoredEvent first = storeAt(NOW.minusSeconds(30));
		buffer.add(UUID.randomUUID(), event(NOW.minusSeconds(20)));

		assertThat(buffer.after(userId, first.id().toString(), NOW)).isEmpty();
	}

	// 명세: 최근 5분 안의 놓친 이벤트만. 더 오래됐으면 보내지 않고 앱이 화면 API로 맞춘다.
	@Test
	void lastEventIdOlderThanFiveMinutesReturnsNothing() {
		StoredEvent old = storeAt(NOW.minus(Duration.ofMinutes(5)).minusSeconds(1));
		storeAt(NOW.minusSeconds(10));

		assertThat(buffer.after(userId, old.id().toString(), NOW)).isEmpty();
	}

	@Test
	void lastEventIdExactlyFiveMinutesOldIsStillReplayed() {
		StoredEvent boundary = storeAt(NOW.minus(Duration.ofMinutes(5)));
		StoredEvent recent = storeAt(NOW.minusSeconds(10));

		assertThat(buffer.after(userId, boundary.id().toString(), NOW)).containsExactly(recent);
	}

	@Test
	void missingOrMalformedLastEventIdReturnsNothing() {
		storeAt(NOW.minusSeconds(10));

		assertThat(buffer.after(userId, null, NOW)).isEmpty();
		assertThat(buffer.after(userId, "evt_1", NOW)).isEmpty();
	}

	@Test
	void pruneDropsEventsOlderThanFiveMinutes() {
		StoredEvent old = storeAt(NOW.minus(Duration.ofMinutes(5)).minusSeconds(1));
		StoredEvent recent = storeAt(NOW.minusSeconds(10));
		Ulid beforeAll = ulidFactory.create(NOW.minus(Duration.ofMinutes(4)).toEpochMilli());

		buffer.prune(NOW);

		assertThat(buffer.after(userId, beforeAll.toString(), NOW)).containsExactly(recent).doesNotContain(old);
	}

	private StoredEvent storeAt(Instant at) {
		StoredEvent event = event(at);
		buffer.add(userId, event);
		return event;
	}

	private StoredEvent event(Instant at) {
		return new StoredEvent(ulidFactory.create(at.toEpochMilli()), "link_request.updated", "{}");
	}
}
