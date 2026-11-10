package com.sonulim.domain.link.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import com.sonulim.global.BaseEntity;

/**
 * 보호자-당사자 연결. 연결 요청 승인(L-3)으로 만들어진다.
 *
 * MVP는 보호자 1명 : 당사자 1명이다. activeGuardianId·activeRecipientId는 살아 있는 연결에만 값이 있는 유니크 컬럼이라,
 * 승인이 동시에 들어와도 DB가 한 사람당 살아 있는 연결 하나만 허용한다(PostgreSQL 유니크 제약은 null을 여러 개 허용).
 * 연결 해제(L-4)에서 두 컬럼을 비운다. 1:N으로 바뀌면 activeGuardianId만 없애면 된다(docs/domains/link.md Pending Decisions).
 */
@Entity
@Table(name = "links", indexes = {
		@Index(name = "idx_links_guardian_id", columnList = "guardianId"),
		@Index(name = "idx_links_recipient_id", columnList = "recipientId")
})
public class Link extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID guardianId;

	@Column(nullable = false)
	private UUID recipientId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LinkStatus status;

	@Column(nullable = false)
	private LocalDateTime linkedAt;

	// 연결 해제(L-4) 시각. 행은 지우지 않는다(Notion API 명세).
	private LocalDateTime endedAt;

	@Column(unique = true)
	private UUID activeGuardianId;

	@Column(unique = true)
	private UUID activeRecipientId;

	// 연결 확인(T-2) 결과. 이 컬럼보다 먼저 만들어진 연결 행은 null이라 nullable로 두고 null은 UNKNOWN으로 읽는다
	// (NOT NULL 컬럼을 추가하면 기존 행이 있는 DB에서 ddl-auto: update가 실패한다).
	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private ConnectionState connectionState;

	// 상대 앱이 마지막으로 ack를 보낸 시각. 타임아웃 뒤에 온 ack도 반영한다(명세).
	private LocalDateTime lastConfirmedAt;

	protected Link() {
	}

	private Link(UUID guardianId, UUID recipientId, LocalDateTime now) {
		this.guardianId = guardianId;
		this.recipientId = recipientId;
		this.status = LinkStatus.ACTIVE;
		this.linkedAt = now;
		this.activeGuardianId = guardianId;
		this.activeRecipientId = recipientId;
		this.connectionState = ConnectionState.UNKNOWN;
	}

	public boolean isActive() {
		return status == LinkStatus.ACTIVE;
	}

	public boolean involves(UUID userId) {
		return guardianId.equals(userId) || recipientId.equals(userId);
	}

	/**
	 * 타임아웃 안에 ack가 왔다.
	 */
	public void connectionConfirmed(LocalDateTime ackedAt) {
		this.connectionState = ConnectionState.CONFIRMED;
		connectionSeenAt(ackedAt);
	}

	/**
	 * 타임아웃 안에 ack가 오지 않았다.
	 */
	public void connectionUnconfirmed() {
		this.connectionState = ConnectionState.UNCONFIRMED;
	}

	/**
	 * 타임아웃 뒤에 온 ack. 상태는 그대로 두고 마지막 확인 시각만 갱신한다(명세 해석, Pending Decisions).
	 */
	public void connectionSeenAt(LocalDateTime ackedAt) {
		if (lastConfirmedAt == null || ackedAt.isAfter(lastConfirmedAt)) {
			this.lastConfirmedAt = ackedAt;
		}
	}

	public ConnectionState getConnectionState() {
		return connectionState != null ? connectionState : ConnectionState.UNKNOWN;
	}

	public LocalDateTime getLastConfirmedAt() {
		return lastConfirmedAt;
	}

	public static Link connect(UUID guardianId, UUID recipientId, LocalDateTime now) {
		return new Link(guardianId, recipientId, now);
	}

	/**
	 * 연결을 끝낸다. 1:1 유니크 컬럼을 비워 두 사람 모두 다시 연결할 수 있게 한다.
	 * 이미 끝난 연결이면 그대로 둔다 — 다시 해제해도 같은 204로 응답한다(docs/domains/link.md Pending Decisions).
	 */
	public void end(LocalDateTime now) {
		if (status == LinkStatus.ENDED) {
			return;
		}
		this.status = LinkStatus.ENDED;
		this.endedAt = now;
		this.activeGuardianId = null;
		this.activeRecipientId = null;
	}

	public LocalDateTime getEndedAt() {
		return endedAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getGuardianId() {
		return guardianId;
	}

	public UUID getRecipientId() {
		return recipientId;
	}

	public LinkStatus getStatus() {
		return status;
	}

	public LocalDateTime getLinkedAt() {
		return linkedAt;
	}
}
