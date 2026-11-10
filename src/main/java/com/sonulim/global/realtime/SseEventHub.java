package com.sonulim.global.realtime;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.ulid.UlidFactory;
import com.sonulim.global.realtime.RecentEventBuffer.StoredEvent;

/**
 * SSE 연결 관리와 이벤트 발행 허브(Notion API 명세 `GET /api/v1/events`).
 *
 * <ul>
 *   <li>사용자별 연결 1개 — 같은 사용자가 다시 연결하면 이전 연결을 닫는다(기기 1대).</li>
 *   <li>이벤트 id는 ULID(같은 밀리초 안에서도 증가). 20초마다 `: ping` 주석으로 연결을 유지한다.</li>
 *   <li>재연결 때 Last-Event-ID가 오면 최근 5분 안의 놓친 이벤트를 다시 보낸다.</li>
 *   <li>연결은 액세스 토큰이 만료되는 시각에 닫는다.</li>
 * </ul>
 *
 * 연결과 최근 이벤트는 서버 메모리에 둔다. 서버를 2대 이상으로 늘리면 Redis pub/sub 등으로 이벤트를 나눠 보내야 한다.
 * ping은 @EnableScheduling 없이 이 허브 전용 스레드로 돌린다.
 */
@Component
public class SseEventHub implements RealtimeEventPublisher {

	static final Duration PING_INTERVAL = Duration.ofSeconds(20);

	private static final Logger log = LoggerFactory.getLogger(SseEventHub.class);

	private final Map<UUID, SseEmitter> connections = new ConcurrentHashMap<>();
	private final RecentEventBuffer recentEvents = new RecentEventBuffer();
	private final UlidFactory ulidFactory;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final ScheduledExecutorService pinger;

	public SseEventHub(ObjectMapper objectMapper, Clock clock) {
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.ulidFactory = UlidFactory.newMonotonicInstance(new SecureRandom(), clock::millis);
		this.pinger = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "sse-ping");
			thread.setDaemon(true);
			return thread;
		});
		long intervalMs = PING_INTERVAL.toMillis();
		pinger.scheduleWithFixedDelay(this::pingSafely, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
	}

	/**
	 * 연결을 열고, Last-Event-ID 뒤의 놓친 이벤트를 다시 보낸다. 연결은 tokenExpiresAt에 닫힌다.
	 */
	SseEmitter connect(UUID userId, Instant tokenExpiresAt, String lastEventId) {
		long timeoutMs = Math.max(1, Duration.between(clock.instant(), tokenExpiresAt).toMillis());
		SseEmitter emitter = new SseEmitter(timeoutMs);
		emitter.onCompletion(() -> connections.remove(userId, emitter));
		emitter.onTimeout(() -> {
			connections.remove(userId, emitter);
			emitter.complete();
		});
		emitter.onError(error -> connections.remove(userId, emitter));

		SseEmitter previous = connections.put(userId, emitter);
		if (previous != null) {
			previous.complete();
		}
		// 응답 헤더는 첫 쓰기 때 나간다 — 바로 ping을 보내 앱이 연결이 열린 것을 첫 ping(20초)까지 기다리지 않게 한다.
		if (!ping(userId, emitter)) {
			return emitter;
		}
		for (StoredEvent event : recentEvents.after(userId, lastEventId, clock.instant())) {
			if (!send(userId, emitter, event)) {
				break;
			}
		}
		return emitter;
	}

	@Override
	public void publish(UUID userId, RealtimeEventType type, Object data) {
		String json = toJson(data);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					deliver(userId, type, json);
				}
			});
			return;
		}
		deliver(userId, type, json);
	}

	void pingAll() {
		recentEvents.prune(clock.instant());
		connections.forEach(this::ping);
	}

	private boolean ping(UUID userId, SseEmitter emitter) {
		try {
			emitter.send(SseEmitter.event().comment(" ping"));
			return true;
		} catch (IOException | IllegalStateException exception) {
			drop(userId, emitter);
			return false;
		}
	}

	@PreDestroy
	void shutdown() {
		pinger.shutdownNow();
		connections.values().forEach(SseEmitter::complete);
		connections.clear();
	}

	private void deliver(UUID userId, RealtimeEventType type, String json) {
		StoredEvent event;
		// id 발급과 보관을 한 번에 해서, 보관된 순서가 id 순서와 같게 한다.
		synchronized (recentEvents) {
			event = new StoredEvent(ulidFactory.create(), type.eventName(), json);
			recentEvents.add(userId, event);
		}
		SseEmitter emitter = connections.get(userId);
		if (emitter != null) {
			send(userId, emitter, event);
		}
	}

	private boolean send(UUID userId, SseEmitter emitter, StoredEvent event) {
		try {
			emitter.send(SseEmitter.event()
					.id(event.id().toString())
					.name(event.name())
					.data(event.json(), MediaType.APPLICATION_JSON));
			return true;
		} catch (IOException | IllegalStateException exception) {
			// 앱이 연결을 끊은 경우. 이벤트는 보관돼 있으니 재연결 때 다시 보낸다.
			drop(userId, emitter);
			return false;
		}
	}

	private void drop(UUID userId, SseEmitter emitter) {
		connections.remove(userId, emitter);
		try {
			emitter.complete();
		} catch (RuntimeException ignored) {
			// 이미 닫힌 연결
		}
	}

	private void pingSafely() {
		try {
			pingAll();
		} catch (RuntimeException exception) {
			log.warn("SSE ping 실패", exception);
		}
	}

	private String toJson(Object data) {
		try {
			return objectMapper.writeValueAsString(data);
		} catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("SSE 이벤트 data를 JSON으로 바꿀 수 없어요: " + data.getClass(), exception);
		}
	}
}
