package com.sonulim.domain.recipient.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.sonulim.global.BaseEntity;

/**
 * 당사자가 보호자를 부른 기록(R-2).
 *
 * <p>다른 도메인은 FK 없이 UUID 값으로만 참조한다(docs/architecture.md).
 * 데이터 모델 초안에는 link_id만 있지만, 연결을 거치지 않고도 "이 기록이 내 것인지" 판단할 수 있도록
 * 보낸 사람·받는 사람을 함께 둔다 — messaging의 Message와 같은 이유다.
 */
@Entity
@Table(name = "recipient_signals")
public class RecipientSignal extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID linkId;

	/** 보낸 사람(당사자). */
	@Column(nullable = false)
	private UUID senderId;

	/** 받는 사람(보호자). */
	@Column(nullable = false)
	private UUID guardianId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private RecipientSignalType type;

	/** 서버가 받은 시각. 본문의 sentAt은 참고용이다(보호자 부르기 API). */
	@Column(nullable = false)
	private LocalDateTime sentAt;

	@Embedded
	private GuardianReceipt receipt = new GuardianReceipt();

	protected RecipientSignal() {
	}

	private RecipientSignal(UUID linkId, UUID senderId, UUID guardianId, RecipientSignalType type,
			LocalDateTime sentAt) {
		this.linkId = linkId;
		this.senderId = senderId;
		this.guardianId = guardianId;
		this.type = type;
		this.sentAt = sentAt;
	}

	public static RecipientSignal send(UUID linkId, UUID senderId, UUID guardianId, RecipientSignalType type,
			LocalDateTime now) {
		return new RecipientSignal(linkId, senderId, guardianId, type, now);
	}

	/**
	 * Hibernate는 임베디드의 모든 컬럼이 null이면 필드 자체를 null로 되돌린다 —
	 * 아직 아무도 받지 않은 기록을 다시 읽으면 receipt가 null이므로 쓸 때마다 여기서 되살린다.
	 */
	private GuardianReceipt receipt() {
		if (receipt == null) {
			receipt = new GuardianReceipt();
		}
		return receipt;
	}

	public void markDelivered(LocalDateTime now) {
		receipt().markDelivered(now);
	}

	public void markOpened(LocalDateTime now) {
		receipt().markOpened(now);
	}

	public UUID getId() {
		return id;
	}

	public UUID getLinkId() {
		return linkId;
	}

	public UUID getSenderId() {
		return senderId;
	}

	public UUID getGuardianId() {
		return guardianId;
	}

	public RecipientSignalType getType() {
		return type;
	}

	public LocalDateTime getSentAt() {
		return sentAt;
	}

	public LocalDateTime getGuardianDeliveredAt() {
		return receipt().getDeliveredAt();
	}

	public LocalDateTime getGuardianOpenedAt() {
		return receipt().getOpenedAt();
	}
}
