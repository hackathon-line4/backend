package com.sonulim.domain.messaging.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.sonulim.global.BaseEntity;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

/**
 * 보호자가 보낸 신호 한 건. 기록의 중심이다.
 *
 * <p>신호 하나는 <b>기기 전달</b>과 <b>터치 응답</b> 두 상태를 따로 가지며, 서버는 두 상태와 단계별 시각을 모두 저장한다(공통 규칙).
 *
 * <p>다른 도메인(users/links/signals)은 FK 없이 UUID 값으로만 참조한다 —
 * docs/architecture.md의 "도메인 패키지는 서로 직접 의존하지 않는다"를 지키기 위해서다.
 */
@Entity
@Table(name = "messages")
public class Message extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/**
	 * 판정 타이머와 당사자의 터치가 동시에 들어올 수 있다(서버 내부 처리). 둘 중 하나만 이기도록 낙관적 락을 건다 —
	 * 진 쪽은 OptimisticLockException으로 끝나고, 타이머는 그 건을 건너뛴다.
	 */
	@Version
	private long version;

	@Column(nullable = false)
	private UUID linkId;

	/** 보낸 사람(보호자). */
	@Column(nullable = false)
	private UUID guardianId;

	/** 받는 사람(당사자). */
	@Column(nullable = false)
	private UUID recipientId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MessageKind kind;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private SignalSnapshot signalSnapshot;

	@Column(nullable = false)
	private boolean emergency;

	// --- 기기 전달 ---

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DeliveryStatus deliveryStatus;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private DeliveryFailureReason failureReason;

	@Column(nullable = false)
	private LocalDateTime requestedAt;

	/** 이 시각까지 ack가 없으면 UNCONFIRMED. 서버가 재시작해도 타이머가 살아남도록 컬럼으로 둔다(서버 내부 처리). */
	@Column(nullable = false)
	private LocalDateTime ackDeadline;

	private LocalDateTime deliveredAt;

	private LocalDateTime unconfirmedAt;

	/** 기기가 받았다고 알려 온 시각. 참고용이고 전달 완료 판정에는 쓰지 않는다(ack API). */
	private LocalDateTime deviceReceivedAt;

	// --- 진동 재생 ---

	@Column(nullable = false)
	private int playCount;

	private LocalDateTime playbackReportedAt;

	// --- 터치 응답 ---

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private ResponseStatus responseStatus;

	private LocalDateTime waitUntil;

	private LocalDateTime respondedAt;

	/** 응답 없음으로 판정한 시각. '더 기다리기'로 WAITING이 되어도 지우지 않고 남긴다(더 기다리기 API). */
	private LocalDateTime noResponseAt;

	/** 응답 없음 판정 뒤에 들어온 터치. */
	@Column(nullable = false)
	private boolean late;

	// --- 긴급 ---

	@Column(nullable = false)
	private boolean emergencyWarning;

	// --- 연결 ---

	/** 다시 보내기로 만들어졌다면 원래 기록 ID. 원래 기록 자체는 바뀌지 않는다. */
	private UUID resendOfId;

	private UUID relatedHelpRequestId;

	/** 기록 상세의 타임라인. Message와 같은 묶음이라 함께 저장·삭제된다. */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "message_id", nullable = false)
	@OrderBy("at asc")
	private final List<MessageEvent> timeline = new ArrayList<>();

	protected Message() {
	}

	private Message(UUID linkId, UUID guardianId, UUID recipientId, MessageKind kind,
			SignalSnapshot signalSnapshot, LocalDateTime now, Duration ackTimeout, UUID resendOfId) {
		this.linkId = linkId;
		this.guardianId = guardianId;
		this.recipientId = recipientId;
		this.kind = kind;
		this.signalSnapshot = signalSnapshot;
		this.emergency = signalSnapshot.category() == SignalCategory.EMERGENCY;
		this.deliveryStatus = DeliveryStatus.REQUESTED;
		this.requestedAt = now;
		this.ackDeadline = now.plus(ackTimeout);
		this.resendOfId = resendOfId;
		record(MessageEventStep.SENT, now);
	}

	/**
	 * 보호자가 신호를 보낸다. 기록을 먼저 만들고 푸시는 그 뒤에 나간다 — 상태는 REQUESTED로 시작한다(신호 보내기 API).
	 */
	public static Message send(UUID linkId, UUID guardianId, UUID recipientId, MessageKind kind,
			SignalSnapshot signalSnapshot, LocalDateTime now, Duration ackTimeout) {
		return new Message(linkId, guardianId, recipientId, kind, signalSnapshot, now, ackTimeout, null);
	}

	/**
	 * 같은 신호로 새 기록을 만든다. 스냅샷은 신호의 <b>현재 값</b>으로 다시 뜨고, 원래 기록은 건드리지 않는다(다시 보내기 API).
	 */
	public static Message resend(Message original, SignalSnapshot currentSnapshot,
			LocalDateTime now, Duration ackTimeout) {
		return new Message(original.linkId, original.guardianId, original.recipientId, original.kind,
				currentSnapshot, now, ackTimeout, original.id);
	}

	/**
	 * 푸시를 보낼 수 없었다. 기록은 남기되 더 진행되지 않는다 — 다시 보내기는 새 기록을 만든다.
	 */
	public void markFailed(DeliveryFailureReason reason, LocalDateTime now) {
		this.deliveryStatus = DeliveryStatus.FAILED;
		this.failureReason = reason;
		record(MessageEventStep.FAILED, now);
	}

	/**
	 * 당사자 앱의 수신 확인. 이때만 '기기 전달 완료'가 된다.
	 *
	 * <p>여러 번 와도 처음 값만 남긴다. UNCONFIRMED 뒤에 와도 DELIVERED로 바꾸고 대기를 시작한다(ack API).
	 * 전송 실패(FAILED) 뒤의 ack는 명세에 정의가 없어 상태를 바꾸지 않는다.
	 *
	 * @param waitMinutes 보호자의 응답 대기 시간. 연습 신호에는 쓰이지 않는다
	 */
	public void ack(LocalDateTime now, LocalDateTime deviceReceivedAt, int waitMinutes) {
		if (deliveryStatus == DeliveryStatus.DELIVERED || deliveryStatus == DeliveryStatus.FAILED) {
			return;
		}
		markDelivered(now);
		this.deviceReceivedAt = deviceReceivedAt;
		if (kind == MessageKind.SIGNAL) {
			startWaiting(now.plusMinutes(waitMinutes));
		}
	}

	/**
	 * 진동을 몇 번 재생했는지 보고받는다. 누적 횟수이므로 지금 값보다 큰 값만 받아들인다 —
	 * 보고가 순서 뒤바뀌어 도착해도 횟수가 줄지 않는다(진동 재생 보고 API).
	 */
	public void reportPlayback(int count, LocalDateTime now) {
		if (count <= playCount) {
			return;
		}
		boolean firstReport = playCount == 0;
		this.playCount = count;
		this.playbackReportedAt = now;
		if (firstReport) {
			timeline.add(MessageEvent.played(now, count));
		}
		else {
			findEvent(MessageEventStep.PLAYED).ifPresent(event -> event.updateCount(count));
		}
	}

	/**
	 * 당사자의 터치를 기록한다. '읽음'이나 '이해함'이 아니라 터치가 있었다는 사실만 남긴다.
	 *
	 * <p>ack 없이 먼저 오면 ack도 같은 시각으로 함께 기록한다. 응답 없음 판정 뒤에 오면 late로 남긴다.
	 * 여러 번 와도 처음 응답만 기록한다(터치 응답 API).
	 */
	public void respond(LocalDateTime now) {
		if (kind == MessageKind.PRACTICE) {
			throw new SonulimException(ErrorCode.MESSAGE_NOT_RESPONDABLE, "연습 신호에는 응답하지 않아도 돼요");
		}
		if (responseStatus == ResponseStatus.RESPONDED) {
			return;
		}
		if (deliveredAt == null) {
			markDelivered(now);
		}
		this.late = responseStatus == ResponseStatus.NO_RESPONSE;
		this.responseStatus = ResponseStatus.RESPONDED;
		this.respondedAt = now;
		record(MessageEventStep.RESPONDED, now);
	}

	/**
	 * 응답 대기 시간을 늘린다. NO_RESPONSE였으면 WAITING으로 되돌리되 판정 시각은 남긴다(더 기다리기 API).
	 */
	public void extendWait(LocalDateTime now, int minutes) {
		if (responseStatus != ResponseStatus.WAITING && responseStatus != ResponseStatus.NO_RESPONSE) {
			throw new SonulimException(ErrorCode.MESSAGE_NOT_WAITING, "이미 응답이 왔어요", null,
					Map.of("responseStatus", String.valueOf(responseStatus)));
		}
		startWaiting(now.plusMinutes(minutes));
		record(MessageEventStep.WAIT_EXTENDED, now);
	}

	/** ack 판정 시간이 지났다. 늦게 ack가 오면 다시 DELIVERED가 된다. */
	public void markUnconfirmed(LocalDateTime now) {
		if (deliveryStatus != DeliveryStatus.REQUESTED) {
			return;
		}
		this.deliveryStatus = DeliveryStatus.UNCONFIRMED;
		this.unconfirmedAt = now;
		record(MessageEventStep.UNCONFIRMED, now);
	}

	/** 대기 시간이 끝났는데 터치가 없었다. */
	public void markNoResponse(LocalDateTime now) {
		if (responseStatus != ResponseStatus.WAITING) {
			return;
		}
		this.responseStatus = ResponseStatus.NO_RESPONSE;
		this.noResponseAt = now;
		record(MessageEventStep.NO_RESPONSE, now);
	}

	/** 긴급 신호가 전달 후 일정 시간 동안 응답이 없었다. */
	public void markEmergencyWarning(LocalDateTime now) {
		if (emergency && responseStatus == ResponseStatus.WAITING && !emergencyWarning) {
			this.emergencyWarning = true;
			record(MessageEventStep.EMERGENCY_WARNING, now);
		}
	}

	public void linkToHelpRequest(UUID helpRequestId) {
		this.relatedHelpRequestId = helpRequestId;
	}

	private void startWaiting(LocalDateTime until) {
		this.responseStatus = ResponseStatus.WAITING;
		this.waitUntil = until;
	}

	private void markDelivered(LocalDateTime now) {
		this.deliveryStatus = DeliveryStatus.DELIVERED;
		this.deliveredAt = now;
		record(MessageEventStep.DELIVERED, now);
	}

	private void record(MessageEventStep step, LocalDateTime at) {
		timeline.add(MessageEvent.of(step, at));
	}

	private Optional<MessageEvent> findEvent(MessageEventStep step) {
		return timeline.stream().filter(event -> event.getStep() == step).findFirst();
	}

	/** 기록 상세의 '더 기다리기' 버튼을 띄울지([전송] 신호 기록 상세). */
	public boolean canExtendWait() {
		return responseStatus == ResponseStatus.WAITING || responseStatus == ResponseStatus.NO_RESPONSE;
	}

	public List<MessageEvent> getTimeline() {
		return Collections.unmodifiableList(timeline);
	}

	/**
	 * 기기 전달부터 응답까지 걸린 시간('전달 후 걸린 시간'). 저장하지 않고 두 시각에서 계산한다.
	 */
	public Long getSecondsToRespond() {
		if (deliveredAt == null || respondedAt == null) {
			return null;
		}
		return Duration.between(deliveredAt, respondedAt).toSeconds();
	}

	public UUID getId() {
		return id;
	}

	public long getVersion() {
		return version;
	}

	public UUID getLinkId() {
		return linkId;
	}

	public UUID getGuardianId() {
		return guardianId;
	}

	public UUID getRecipientId() {
		return recipientId;
	}

	public MessageKind getKind() {
		return kind;
	}

	public SignalSnapshot getSignalSnapshot() {
		return signalSnapshot;
	}

	public boolean isEmergency() {
		return emergency;
	}

	public DeliveryStatus getDeliveryStatus() {
		return deliveryStatus;
	}

	public DeliveryFailureReason getFailureReason() {
		return failureReason;
	}

	public LocalDateTime getRequestedAt() {
		return requestedAt;
	}

	public LocalDateTime getAckDeadline() {
		return ackDeadline;
	}

	public LocalDateTime getDeliveredAt() {
		return deliveredAt;
	}

	public LocalDateTime getUnconfirmedAt() {
		return unconfirmedAt;
	}

	public LocalDateTime getDeviceReceivedAt() {
		return deviceReceivedAt;
	}

	public int getPlayCount() {
		return playCount;
	}

	public LocalDateTime getPlaybackReportedAt() {
		return playbackReportedAt;
	}

	public ResponseStatus getResponseStatus() {
		return responseStatus;
	}

	public LocalDateTime getWaitUntil() {
		return waitUntil;
	}

	public LocalDateTime getRespondedAt() {
		return respondedAt;
	}

	public LocalDateTime getNoResponseAt() {
		return noResponseAt;
	}

	public boolean isLate() {
		return late;
	}

	public boolean isEmergencyWarning() {
		return emergencyWarning;
	}

	public UUID getResendOfId() {
		return resendOfId;
	}

	public UUID getRelatedHelpRequestId() {
		return relatedHelpRequestId;
	}
}
