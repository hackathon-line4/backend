package com.sonulim.domain.messaging.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageTest {

	private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 11, 4, 18, 2, 1);
	private static final Duration ACK_TIMEOUT = Duration.ofSeconds(60);
	private static final int WAIT_MINUTES = 10;

	private static final UUID LINK = UUID.randomUUID();
	private static final UUID GUARDIAN = UUID.randomUUID();
	private static final UUID RECIPIENT = UUID.randomUUID();

	private static SignalSnapshot snapshot(SignalCategory category) {
		return new SignalSnapshot(UUID.randomUUID(), "약 먹을 시간", "저녁 약을 먹자는 뜻",
				category, "pill", List.of("SHORT", "SHORT", "LONG"), List.of(150, 150, 150, 150, 1000), 1900);
	}

	private static Message sent(MessageKind kind, SignalCategory category) {
		return Message.send(LINK, GUARDIAN, RECIPIENT, kind, snapshot(category), SENT_AT, ACK_TIMEOUT);
	}

	private static Message sent() {
		return sent(MessageKind.SIGNAL, SignalCategory.DAILY);
	}

	@Test
	void sendingStartsAsRequestedWithoutResponseStateAndWithAnAckDeadline() {
		Message message = sent();

		assertThat(message.getDeliveryStatus()).isEqualTo(DeliveryStatus.REQUESTED);
		assertThat(message.getRequestedAt()).isEqualTo(SENT_AT);
		assertThat(message.getAckDeadline()).isEqualTo(SENT_AT.plusSeconds(60));
		// 기기 전달 전에는 응답 상태를 보여 주지 않는다
		assertThat(message.getResponseStatus()).isNull();
		assertThat(message.getDeliveredAt()).isNull();
	}

	@Test
	void emergencyIsDerivedFromTheSignalCategory() {
		assertThat(sent(MessageKind.SIGNAL, SignalCategory.EMERGENCY).isEmergency()).isTrue();
		assertThat(sent().isEmergency()).isFalse();
	}

	@Test
	void ackMarksDeliveredWithServerTimeAndStartsWaiting() {
		Message message = sent();
		LocalDateTime serverNow = SENT_AT.plusSeconds(2);
		LocalDateTime deviceTime = SENT_AT.plusSeconds(5); // 기기 시각이 서버와 어긋나 있어도

		message.ack(serverNow, deviceTime, WAIT_MINUTES);

		assertThat(message.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
		// 전달 시각은 서버가 받은 시각이고, 기기 시각은 참고용으로만 남는다
		assertThat(message.getDeliveredAt()).isEqualTo(serverNow);
		assertThat(message.getDeviceReceivedAt()).isEqualTo(deviceTime);
		assertThat(message.getResponseStatus()).isEqualTo(ResponseStatus.WAITING);
		assertThat(message.getWaitUntil()).isEqualTo(serverNow.plusMinutes(WAIT_MINUTES));
	}

	@Test
	void practiceAckDeliversButNeverStartsWaiting() {
		Message practice = sent(MessageKind.PRACTICE, SignalCategory.DAILY);

		practice.ack(SENT_AT.plusSeconds(2), SENT_AT.plusSeconds(2), WAIT_MINUTES);

		assertThat(practice.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
		assertThat(practice.getResponseStatus()).isNull();
		assertThat(practice.getWaitUntil()).isNull();
	}

	@Test
	void repeatedAckKeepsTheFirstValues() {
		Message message = sent();
		LocalDateTime first = SENT_AT.plusSeconds(2);

		message.ack(first, first, WAIT_MINUTES);
		message.ack(SENT_AT.plusSeconds(40), SENT_AT.plusSeconds(40), WAIT_MINUTES);

		assertThat(message.getDeliveredAt()).isEqualTo(first);
		assertThat(message.getWaitUntil()).isEqualTo(first.plusMinutes(WAIT_MINUTES));
	}

	@Test
	void lateAckAfterUnconfirmedStillDeliversAndStartsWaiting() {
		Message message = sent();
		message.markUnconfirmed(SENT_AT.plusSeconds(60));

		LocalDateTime lateAck = SENT_AT.plusSeconds(90);
		message.ack(lateAck, lateAck, WAIT_MINUTES);

		assertThat(message.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
		assertThat(message.getResponseStatus()).isEqualTo(ResponseStatus.WAITING);
		// 판정이 있었다는 사실 자체는 지우지 않는다
		assertThat(message.getUnconfirmedAt()).isEqualTo(SENT_AT.plusSeconds(60));
	}

	@Test
	void ackAfterSendFailureDoesNotChangeTheRecord() {
		Message message = sent();
		message.markFailed(DeliveryFailureReason.RECIPIENT_NO_DEVICE, SENT_AT.plusSeconds(1));

		message.ack(SENT_AT.plusSeconds(5), SENT_AT.plusSeconds(5), WAIT_MINUTES);

		assertThat(message.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
		assertThat(message.getFailureReason()).isEqualTo(DeliveryFailureReason.RECIPIENT_NO_DEVICE);
		assertThat(message.getDeliveredAt()).isNull();
	}

	@Test
	void playbackCountOnlyGrows() {
		Message message = sent();

		message.reportPlayback(3, SENT_AT.plusSeconds(9));
		message.reportPlayback(1, SENT_AT.plusSeconds(12)); // 순서가 뒤바뀌어 도착한 보고

		assertThat(message.getPlayCount()).isEqualTo(3);
		assertThat(message.getPlaybackReportedAt()).isEqualTo(SENT_AT.plusSeconds(9));
	}

	@Test
	void respondRecordsTouchAndComputesSecondsFromDelivery() {
		Message message = sent();
		LocalDateTime delivered = SENT_AT.plusSeconds(2);
		message.ack(delivered, delivered, WAIT_MINUTES);

		message.respond(delivered.plusSeconds(139));

		assertThat(message.getResponseStatus()).isEqualTo(ResponseStatus.RESPONDED);
		assertThat(message.isLate()).isFalse();
		assertThat(message.getSecondsToRespond()).isEqualTo(139L);
	}

	@Test
	void respondBeforeAckRecordsTheAckAtTheSameMoment() {
		Message message = sent();
		LocalDateTime touch = SENT_AT.plusSeconds(4);

		message.respond(touch);

		assertThat(message.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
		assertThat(message.getDeliveredAt()).isEqualTo(touch);
		assertThat(message.getSecondsToRespond()).isZero();
	}

	@Test
	void respondAfterNoResponseIsMarkedLate() {
		Message message = sent();
		LocalDateTime delivered = SENT_AT.plusSeconds(2);
		message.ack(delivered, delivered, WAIT_MINUTES);
		message.markNoResponse(delivered.plusMinutes(WAIT_MINUTES));

		message.respond(delivered.plusMinutes(11));

		assertThat(message.getResponseStatus()).isEqualTo(ResponseStatus.RESPONDED);
		assertThat(message.isLate()).isTrue();
	}

	@Test
	void repeatedRespondKeepsTheFirstTouch() {
		Message message = sent();
		LocalDateTime delivered = SENT_AT.plusSeconds(2);
		message.ack(delivered, delivered, WAIT_MINUTES);
		LocalDateTime firstTouch = delivered.plusSeconds(10);

		message.respond(firstTouch);
		message.respond(delivered.plusSeconds(99));

		assertThat(message.getRespondedAt()).isEqualTo(firstTouch);
		assertThat(message.getSecondsToRespond()).isEqualTo(10L);
	}

	@Test
	void practiceCannotBeRespondedTo() {
		Message practice = sent(MessageKind.PRACTICE, SignalCategory.DAILY);
		practice.ack(SENT_AT.plusSeconds(2), SENT_AT.plusSeconds(2), WAIT_MINUTES);

		assertThatThrownBy(() -> practice.respond(SENT_AT.plusSeconds(5)))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.MESSAGE_NOT_RESPONDABLE);
	}

	@Test
	void extendWaitAfterNoResponseGoesBackToWaitingButKeepsTheVerdictTime() {
		Message message = sent();
		LocalDateTime delivered = SENT_AT.plusSeconds(2);
		message.ack(delivered, delivered, WAIT_MINUTES);
		LocalDateTime verdict = delivered.plusMinutes(WAIT_MINUTES);
		message.markNoResponse(verdict);

		LocalDateTime pressed = verdict.plusSeconds(30);
		message.extendWait(pressed, 10);

		assertThat(message.getResponseStatus()).isEqualTo(ResponseStatus.WAITING);
		assertThat(message.getWaitUntil()).isEqualTo(pressed.plusMinutes(10));
		// 판정이 한 번 있었다는 기록은 남는다
		assertThat(message.getNoResponseAt()).isEqualTo(verdict);
	}

	@Test
	void extendWaitIsRejectedOnceResponded() {
		Message message = sent();
		LocalDateTime delivered = SENT_AT.plusSeconds(2);
		message.ack(delivered, delivered, WAIT_MINUTES);
		message.respond(delivered.plusSeconds(5));

		assertThatThrownBy(() -> message.extendWait(delivered.plusSeconds(10), 10))
				.isInstanceOf(SonulimException.class)
				.satisfies(thrown -> {
					SonulimException exception = (SonulimException) thrown;
					assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MESSAGE_NOT_WAITING);
					assertThat(exception.getDetails()).containsEntry("responseStatus", "RESPONDED");
				});
	}

	@Test
	void noResponseVerdictDoesNotOverrideATouchThatAlreadyArrived() {
		Message message = sent();
		LocalDateTime delivered = SENT_AT.plusSeconds(2);
		message.ack(delivered, delivered, WAIT_MINUTES);
		message.respond(delivered.plusSeconds(30));

		// 판정 타이머가 한 박자 늦게 돌아도 응답을 덮지 않는다
		message.markNoResponse(delivered.plusMinutes(WAIT_MINUTES));

		assertThat(message.getResponseStatus()).isEqualTo(ResponseStatus.RESPONDED);
		assertThat(message.getNoResponseAt()).isNull();
	}

	@Test
	void emergencyWarningOnlyAppliesWhileAnEmergencyIsStillWaiting() {
		Message emergency = sent(MessageKind.SIGNAL, SignalCategory.EMERGENCY);
		LocalDateTime delivered = SENT_AT.plusSeconds(2);
		emergency.ack(delivered, delivered, WAIT_MINUTES);
		emergency.markEmergencyWarning(delivered.plusSeconds(30));
		assertThat(emergency.isEmergencyWarning()).isTrue();

		Message ordinary = sent();
		ordinary.ack(delivered, delivered, WAIT_MINUTES);
		ordinary.markEmergencyWarning(delivered.plusSeconds(30));
		assertThat(ordinary.isEmergencyWarning()).isFalse();
	}

	@Test
	void timelineFollowsTheStepsTheDetailScreenShows() {
		Message message = sent();
		LocalDateTime delivered = SENT_AT.plusSeconds(2);
		message.ack(delivered, delivered, WAIT_MINUTES);
		message.reportPlayback(1, delivered.plusSeconds(4));
		message.reportPlayback(3, delivered.plusSeconds(6));
		message.markNoResponse(delivered.plusMinutes(WAIT_MINUTES));

		// 보냄 → 기기에 도착 → 진동 재생됨 → 화면 터치 없음
		assertThat(message.getTimeline()).extracting(MessageEvent::getStep)
				.containsExactly(MessageEventStep.SENT, MessageEventStep.DELIVERED,
						MessageEventStep.PLAYED, MessageEventStep.NO_RESPONSE);
		// 두 번째 재생 보고는 줄을 새로 만들지 않고 횟수만 올린다
		MessageEvent played = message.getTimeline().get(2);
		assertThat(played.getAt()).isEqualTo(delivered.plusSeconds(4));
		assertThat(played.getMeta()).containsEntry("count", 3);
	}

	@Test
	void extendingTheWaitLeavesBothTheVerdictAndTheExtensionOnTheTimeline() {
		Message message = sent();
		LocalDateTime delivered = SENT_AT.plusSeconds(2);
		message.ack(delivered, delivered, WAIT_MINUTES);
		message.markNoResponse(delivered.plusMinutes(WAIT_MINUTES));
		message.extendWait(delivered.plusMinutes(11), 10);

		assertThat(message.getTimeline()).extracting(MessageEvent::getStep)
				.containsExactly(MessageEventStep.SENT, MessageEventStep.DELIVERED,
						MessageEventStep.NO_RESPONSE, MessageEventStep.WAIT_EXTENDED);
		assertThat(message.canExtendWait()).isTrue();
	}

	@Test
	void respondingBeforeAckStillLeavesADeliveredStepBeforeTheResponse() {
		Message message = sent();

		message.respond(SENT_AT.plusSeconds(4));

		assertThat(message.getTimeline()).extracting(MessageEvent::getStep)
				.containsExactly(MessageEventStep.SENT, MessageEventStep.DELIVERED, MessageEventStep.RESPONDED);
		assertThat(message.canExtendWait()).isFalse();
	}

	@Test
	void resendCopiesTheLinkAndPointsAtTheOriginalWithAFreshSnapshot() {
		Message original = sent();
		SignalSnapshot renamed = new SignalSnapshot(original.getSignalSnapshot().signalId(),
				"이름이 바뀐 신호", "설명도 바뀜", SignalCategory.DAILY, "pill", List.of("LONG", "LONG"), List.of(1000, 150, 1000), 2100);

		Message resent = Message.resend(original, renamed, SENT_AT.plusMinutes(12), ACK_TIMEOUT);

		assertThat(resent.getLinkId()).isEqualTo(original.getLinkId());
		assertThat(resent.getGuardianId()).isEqualTo(original.getGuardianId());
		assertThat(resent.getRecipientId()).isEqualTo(original.getRecipientId());
		assertThat(resent.getDeliveryStatus()).isEqualTo(DeliveryStatus.REQUESTED);
		assertThat(resent.getSignalSnapshot().name()).isEqualTo("이름이 바뀐 신호");
		// 원래 기록은 그대로다
		assertThat(original.getSignalSnapshot().name()).isEqualTo("약 먹을 시간");
		assertThat(original.getResendOfId()).isNull();
	}
}
