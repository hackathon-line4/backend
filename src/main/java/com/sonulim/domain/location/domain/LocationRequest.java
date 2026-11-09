package com.sonulim.domain.location.domain;

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
import jakarta.persistence.Table;

import com.sonulim.global.BaseEntity;

/**
 * 보호자가 당사자 휴대폰에 현재 위치를 1회 요청한 기록(P-1).
 *
 * <p>다른 도메인은 FK 없이 UUID 값으로만 참조한다(docs/architecture.md).
 */
@Entity
@Table(name = "location_requests")
public class LocationRequest extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID linkId;

	/** 요청한 사람(보호자). */
	@Column(nullable = false)
	private UUID guardianId;

	/** 위치를 보내야 하는 사람(당사자). */
	@Column(nullable = false)
	private UUID recipientId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LocationRequestStatus status;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private LocationFailureReason failureReason;

	@Column(nullable = false)
	private LocalDateTime requestedAt;

	/** 이 시각까지 응답이 없으면 TIMED_OUT. 서버가 재시작해도 남도록 컬럼으로 둔다. */
	@Column(nullable = false)
	private LocalDateTime timeoutAt;

	protected LocationRequest() {
	}

	private LocationRequest(UUID linkId, UUID guardianId, UUID recipientId, LocalDateTime now, Duration timeout) {
		this.linkId = linkId;
		this.guardianId = guardianId;
		this.recipientId = recipientId;
		this.status = LocationRequestStatus.PENDING;
		this.requestedAt = now;
		this.timeoutAt = now.plus(timeout);
	}

	public static LocationRequest open(UUID linkId, UUID guardianId, UUID recipientId,
			LocalDateTime now, Duration timeout) {
		return new LocationRequest(linkId, guardianId, recipientId, now, timeout);
	}

	/**
	 * 당사자가 위치를 보내 왔다.
	 *
	 * <p>이미 시간 초과로 끝난 요청이면 상태를 되돌리지 않는다 — 보호자 화면은 이미 '실패'를 보여 줬기 때문이다.
	 * 다만 <b>늦게 온 위치도 마지막 위치에는 반영한다</b>(위치 보내기 API). 그 저장은 호출하는 쪽이 한다.
	 *
	 * @return 이번 응답으로 요청이 완료됐으면 true
	 */
	public boolean markFulfilled() {
		if (status != LocationRequestStatus.PENDING) {
			return false;
		}
		this.status = LocationRequestStatus.FULFILLED;
		return true;
	}

	/** 당사자 기기가 위치를 얻지 못했거나 공유가 꺼져 있었다. */
	public boolean markFailed(LocationFailureReason reason) {
		if (status != LocationRequestStatus.PENDING) {
			return false;
		}
		this.status = LocationRequestStatus.FAILED;
		this.failureReason = reason;
		return true;
	}

	/** 제한 시간이 지났다. 그 뒤에 위치가 와도 이 상태는 그대로 둔다. */
	public boolean markTimedOut() {
		if (status != LocationRequestStatus.PENDING) {
			return false;
		}
		this.status = LocationRequestStatus.TIMED_OUT;
		return true;
	}

	public boolean isPending() {
		return status == LocationRequestStatus.PENDING;
	}

	public UUID getId() {
		return id;
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

	public LocationRequestStatus getStatus() {
		return status;
	}

	public LocationFailureReason getFailureReason() {
		return failureReason;
	}

	public LocalDateTime getRequestedAt() {
		return requestedAt;
	}

	public LocalDateTime getTimeoutAt() {
		return timeoutAt;
	}
}
