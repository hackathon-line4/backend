package com.sonulim.domain.link.domain;

import java.time.Duration;
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
 * 연결 확인(T-2): 한쪽이 요청하면 상대 앱이 ack를 보낸다. 타임아웃 안에 ack가 오면 CONFIRMED, 아니면 UNCONFIRMED.
 *
 * 타임아웃 판정은 서버 타이머 없이 확인을 읽거나 바꿀 때 한다(resolveIfDue).
 */
@Entity
@Table(name = "connection_checks", indexes = @Index(name = "idx_connection_checks_link_id", columnList = "linkId"))
public class ConnectionCheck extends BaseEntity {

	/**
	 * 명세의 "30초(제안)". 확정 전이다(docs/domains/link.md Pending Decisions "연결 확인 타임아웃") — 확정되면 이 값만 바꾼다.
	 */
	public static final Duration TIMEOUT = Duration.ofSeconds(30);

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID linkId;

	@Column(nullable = false)
	private UUID requestedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ConnectionCheckStatus status;

	@Column(nullable = false)
	private LocalDateTime requestedAt;

	@Column(nullable = false)
	private LocalDateTime timeoutAt;

	// 타임아웃 안에 온 첫 ack 시각. 늦은 ack는 여기에 남기지 않는다(명세 해석, Pending Decisions).
	private LocalDateTime confirmedAt;

	// 첫 ack 시각(늦은 ack 포함). "여러 번 와도 처음 한 번만 기록"을 지키기 위한 내부 값이라 응답에 내보내지 않는다.
	private LocalDateTime ackedAt;

	protected ConnectionCheck() {
	}

	private ConnectionCheck(UUID linkId, UUID requestedBy, LocalDateTime now) {
		this.linkId = linkId;
		this.requestedBy = requestedBy;
		this.status = ConnectionCheckStatus.PENDING;
		this.requestedAt = now;
		this.timeoutAt = now.plus(TIMEOUT);
	}

	public static ConnectionCheck request(UUID linkId, UUID requestedBy, LocalDateTime now) {
		return new ConnectionCheck(linkId, requestedBy, now);
	}

	/**
	 * 대기 중인데 timeoutAt이 지났으면 UNCONFIRMED로 바꾼다. 바꿨으면 true.
	 */
	public boolean resolveIfDue(LocalDateTime now) {
		if (status == ConnectionCheckStatus.PENDING && !timeoutAt.isAfter(now)) {
			status = ConnectionCheckStatus.UNCONFIRMED;
			return true;
		}
		return false;
	}

	/**
	 * 상대 앱의 응답. 처음 한 번만 기록한다. 타임아웃 판정(resolveIfDue)을 먼저 한 뒤 부른다.
	 */
	public AckOutcome ack(LocalDateTime now) {
		if (ackedAt != null) {
			return AckOutcome.ALREADY_ACKED;
		}
		ackedAt = now;
		if (status == ConnectionCheckStatus.PENDING) {
			status = ConnectionCheckStatus.CONFIRMED;
			confirmedAt = now;
			return AckOutcome.CONFIRMED;
		}
		return AckOutcome.LATE;
	}

	public UUID getId() {
		return id;
	}

	public UUID getLinkId() {
		return linkId;
	}

	public UUID getRequestedBy() {
		return requestedBy;
	}

	public ConnectionCheckStatus getStatus() {
		return status;
	}

	public LocalDateTime getRequestedAt() {
		return requestedAt;
	}

	public LocalDateTime getTimeoutAt() {
		return timeoutAt;
	}

	public LocalDateTime getConfirmedAt() {
		return confirmedAt;
	}

	public enum AckOutcome {
		/** 타임아웃 안에 온 첫 ack */
		CONFIRMED,
		/** 타임아웃 뒤에 온 첫 ack — 연결이 살아 있다는 증거라 Link.lastConfirmedAt만 갱신한다(명세) */
		LATE,
		/** 이미 ack를 받은 확인 */
		ALREADY_ACKED
	}
}
