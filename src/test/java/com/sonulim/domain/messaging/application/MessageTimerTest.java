package com.sonulim.domain.messaging.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.sonulim.domain.messaging.domain.DeliveryStatus;
import com.sonulim.domain.messaging.domain.Message;
import com.sonulim.domain.messaging.domain.MessageEvent;
import com.sonulim.domain.messaging.domain.MessageEventStep;
import com.sonulim.domain.messaging.domain.MessageKind;
import com.sonulim.domain.messaging.domain.MessageRepository;
import com.sonulim.domain.messaging.domain.ResponseStatus;
import com.sonulim.domain.messaging.domain.SignalCategory;
import com.sonulim.domain.messaging.domain.SignalSnapshot;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 타이머는 커밋된 상태를 보고 판정하므로 테스트를 @Transactional로 묶지 않는다 — 대신 쓴 데이터를 직접 지운다.
 */
@SpringBootTest
class MessageTimerTest {

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private MessageTimerScheduler scheduler;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private static final int WAIT_MINUTES = 10;

	@BeforeEach
	void clear() {
		messageRepository.deleteAll();
	}

	private void deliverAt(UUID id, LocalDateTime deliveredAt) {
		inNewTransaction(loaded -> loaded.ack(deliveredAt, deliveredAt, WAIT_MINUTES), id);
	}

	private Message persistedMessage(SignalCategory category, LocalDateTime sentAt, Duration ackTimeout) {
		Message message = Message.send(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				MessageKind.SIGNAL,
				new SignalSnapshot(UUID.randomUUID(), "약 먹을 시간", null, category, "pill",
						List.of("SHORT", "SHORT", "LONG"), List.of(150, 150, 150, 150, 1000), 1900),
				sentAt, ackTimeout);
		return messageRepository.save(message);
	}

	@Test
	void ackDeadlinePassedWithoutAckBecomesUnconfirmed() {
		// 60초 전에 보냈고 ack가 없다
		persistedMessage(SignalCategory.DAILY, LocalDateTime.now().minusMinutes(5), Duration.ofSeconds(60));

		scheduler.sweep();

		Message judged = messageRepository.findAll().get(0);
		assertThat(judged.getDeliveryStatus()).isEqualTo(DeliveryStatus.UNCONFIRMED);
		assertThat(judged.getUnconfirmedAt()).isNotNull();
		assertThat(timelineOf(judged.getId()))
				.containsExactly(MessageEventStep.SENT, MessageEventStep.UNCONFIRMED);
	}

	@Test
	void ackArrivingInTimeIsNotJudged() {
		Message message = persistedMessage(SignalCategory.DAILY, LocalDateTime.now(), Duration.ofSeconds(60));
		inNewTransaction(loaded -> loaded.ack(LocalDateTime.now(), LocalDateTime.now(), WAIT_MINUTES),
				message.getId());

		scheduler.sweep();

		assertThat(messageRepository.findAll().get(0).getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
	}

	@Test
	void waitExpiredWithoutTouchBecomesNoResponse() {
		Message message = persistedMessage(SignalCategory.DAILY, LocalDateTime.now().minusMinutes(30),
				Duration.ofSeconds(60));
		// 20분 전에 전달됐고 대기 10분이 이미 지났다
		deliverAt(message.getId(), LocalDateTime.now().minusMinutes(20));

		scheduler.sweep();

		Message judged = messageRepository.findAll().get(0);
		assertThat(judged.getResponseStatus()).isEqualTo(ResponseStatus.NO_RESPONSE);
		assertThat(judged.getNoResponseAt()).isNotNull();
		assertThat(timelineOf(judged.getId())).contains(MessageEventStep.NO_RESPONSE);
	}

	@Test
	void aTouchThatLandsBeforeTheSweepIsNotOverwrittenByTheVerdict() {
		Message message = persistedMessage(SignalCategory.DAILY, LocalDateTime.now().minusMinutes(30),
				Duration.ofSeconds(60));
		deliverAt(message.getId(), LocalDateTime.now().minusMinutes(20));

		// 대기가 이미 끝난 기록에 터치가 늦게 들어온다
		inNewTransaction(loaded -> loaded.respond(LocalDateTime.now()), message.getId());

		scheduler.sweep();

		Message afterSweep = messageRepository.findAll().get(0);
		assertThat(afterSweep.getResponseStatus()).isEqualTo(ResponseStatus.RESPONDED);
		assertThat(afterSweep.getNoResponseAt()).isNull();
		assertThat(timelineOf(afterSweep.getId())).doesNotContain(MessageEventStep.NO_RESPONSE);
	}

	@Test
	void emergencyWaitingTooLongRaisesAWarningOnlyOnce() {
		Message message = persistedMessage(SignalCategory.EMERGENCY, LocalDateTime.now().minusMinutes(2),
				Duration.ofSeconds(60));
		deliverAt(message.getId(), LocalDateTime.now().minusMinutes(1));

		scheduler.sweep();
		scheduler.sweep();

		Message judged = messageRepository.findAll().get(0);
		assertThat(judged.isEmergencyWarning()).isTrue();
		// 두 번 훑어도 경고 줄은 하나다
		assertThat(timelineOf(judged.getId()))
				.filteredOn(step -> step == MessageEventStep.EMERGENCY_WARNING).hasSize(1);
	}

	@Test
	void ordinarySignalWaitingTooLongGetsNoEmergencyWarning() {
		Message message = persistedMessage(SignalCategory.DAILY, LocalDateTime.now().minusMinutes(2),
				Duration.ofSeconds(60));
		deliverAt(message.getId(), LocalDateTime.now().minusMinutes(1));

		scheduler.sweep();

		assertThat(messageRepository.findAll().get(0).isEmergencyWarning()).isFalse();
	}

	/** 타이머는 커밋된 상태를 보므로 준비 작업도 각각 커밋한다. */
	private void inNewTransaction(Consumer<Message> work, UUID id) {
		transactionTemplate.executeWithoutResult(status -> {
			Message loaded = messageRepository.findById(id).orElseThrow();
			work.accept(loaded);
			messageRepository.save(loaded);
		});
	}

	/**
	 * 타임라인은 LAZY라 트랜잭션 안에서 읽어야 한다 — 목록 조회에서 N+1이 나지 않도록 LAZY를 유지하고,
	 * 기록 상세는 나중에 fetch join으로 가져온다.
	 */
	private List<MessageEventStep> timelineOf(UUID id) {
		return transactionTemplate.execute(status -> messageRepository.findById(id).orElseThrow()
				.getTimeline().stream().map(MessageEvent::getStep).toList());
	}
}
