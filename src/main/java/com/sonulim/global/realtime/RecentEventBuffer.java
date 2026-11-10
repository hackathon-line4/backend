package com.sonulim.global.realtime;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.github.f4b6a3.ulid.Ulid;

/**
 * 재연결 때 다시 보낼 사용자별 최근 이벤트. 최근 5분만 보관한다(Notion API 명세).
 *
 * 서버 메모리에 저장한다 — 재시작하면 비고(앱은 연결이 열릴 때마다 화면 API로 맞춘다), 서버를 2대 이상으로 늘리면
 * Redis 같은 공유 저장소가 필요하다.
 *
 * 사용자별 목록은 ConcurrentHashMap.compute 안에서만 읽고 바꾼다 — 같은 사용자에 대한 추가·조회·정리가 서로 끼어들지 않는다.
 */
final class RecentEventBuffer {

	static final Duration RETENTION = Duration.ofMinutes(5);

	private final Map<UUID, Deque<StoredEvent>> eventsByUser = new ConcurrentHashMap<>();

	void add(UUID userId, StoredEvent event) {
		eventsByUser.compute(userId, (key, events) -> {
			Deque<StoredEvent> target = events != null ? events : new ArrayDeque<>();
			target.addLast(event);
			return target;
		});
	}

	/**
	 * lastEventId 뒤에 생긴 이벤트를 생긴 순서대로. lastEventId가 ULID가 아니거나 5분보다 오래됐으면 보내지 않는다 —
	 * 그때는 앱이 화면 API로 맞춘다.
	 */
	List<StoredEvent> after(UUID userId, String lastEventId, Instant now) {
		if (lastEventId == null || !Ulid.isValid(lastEventId.trim())) {
			return List.of();
		}
		Ulid lastId = Ulid.from(lastEventId.trim());
		Instant oldestKept = now.minus(RETENTION);
		if (lastId.getInstant().isBefore(oldestKept)) {
			return List.of();
		}
		List<StoredEvent> missed = new ArrayList<>();
		eventsByUser.computeIfPresent(userId, (key, events) -> {
			for (StoredEvent event : events) {
				if (event.id().compareTo(lastId) > 0 && !event.id().getInstant().isBefore(oldestKept)) {
					missed.add(event);
				}
			}
			return events;
		});
		return missed;
	}

	void prune(Instant now) {
		Instant oldestKept = now.minus(RETENTION);
		for (UUID userId : eventsByUser.keySet()) {
			eventsByUser.computeIfPresent(userId, (key, events) -> {
				while (!events.isEmpty() && events.peekFirst().id().getInstant().isBefore(oldestKept)) {
					events.pollFirst();
				}
				return events.isEmpty() ? null : events;
			});
		}
	}

	/**
	 * 보낸 그대로 다시 보낼 수 있게 data를 JSON 문자열로 들고 있다.
	 */
	record StoredEvent(Ulid id, String name, String json) {
	}
}
