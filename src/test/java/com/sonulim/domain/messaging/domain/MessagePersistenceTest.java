package com.sonulim.domain.messaging.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 신호 스냅샷은 jsonb 컬럼에 record로 저장된다 — 쓰고 다시 읽었을 때 그대로 돌아오는지 확인한다.
 */
@SpringBootTest
@Transactional
class MessagePersistenceTest {

	@Autowired
	private EntityManager entityManager;

	@Test
	void signalSnapshotSurvivesARoundTripThroughTheJsonColumn() {
		SignalSnapshot snapshot = new SignalSnapshot(UUID.randomUUID(), "약 먹을 시간", "저녁 약을 먹자는 뜻",
				SignalCategory.DAILY, "pill", List.of("SHORT", "SHORT", "LONG"), 1900);
		Message message = Message.send(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				MessageKind.SIGNAL, snapshot, LocalDateTime.now(), Duration.ofSeconds(60));

		entityManager.persist(message);
		entityManager.flush();
		entityManager.clear();

		Message reloaded = entityManager.find(Message.class, message.getId());
		assertThat(reloaded.getSignalSnapshot()).isEqualTo(snapshot);
		assertThat(reloaded.getSignalSnapshot().rhythm()).containsExactly("SHORT", "SHORT", "LONG");
		assertThat(reloaded.getDeliveryStatus()).isEqualTo(DeliveryStatus.REQUESTED);
		assertThat(reloaded.getResponseStatus()).isNull();
	}

	@Test
	void stateChangesArePersisted() {
		Message message = Message.send(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				MessageKind.SIGNAL,
				new SignalSnapshot(UUID.randomUUID(), "위험해", null, SignalCategory.EMERGENCY, "siren",
						List.of("LONG", "LONG", "LONG"), 3300),
				LocalDateTime.now(), Duration.ofSeconds(60));
		entityManager.persist(message);

		LocalDateTime delivered = LocalDateTime.now().plusSeconds(2);
		message.ack(delivered, delivered, 10);
		message.reportPlayback(3, delivered.plusSeconds(6));
		message.respond(delivered.plusSeconds(30));
		entityManager.flush();
		entityManager.clear();

		Message reloaded = entityManager.find(Message.class, message.getId());
		assertThat(reloaded.isEmergency()).isTrue();
		assertThat(reloaded.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
		assertThat(reloaded.getResponseStatus()).isEqualTo(ResponseStatus.RESPONDED);
		assertThat(reloaded.getSecondsToRespond()).isEqualTo(30L);
		// 타임라인도 함께 저장되고 시각순으로 돌아온다
		assertThat(reloaded.getTimeline()).extracting(MessageEvent::getStep)
				.containsExactly(MessageEventStep.SENT, MessageEventStep.DELIVERED,
						MessageEventStep.PLAYED, MessageEventStep.RESPONDED);
		assertThat(reloaded.getTimeline().get(2).getMeta()).containsEntry("count", 3);
	}
}
