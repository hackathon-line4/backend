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
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

/**
 * 당사자가 보낸 도움 요청(R-3). 가장 강한 알림으로 전달되고, 보호자가 열어 볼 때까지 재알림한다.
 */
@Entity
@Table(name = "help_requests")
public class HelpRequest extends BaseEntity {

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

	/** 서버가 받은 시각. 본문의 sentAt은 참고용이다. */
	@Column(nullable = false)
	private LocalDateTime sentAt;

	@Embedded
	private GuardianReceipt receipt = new GuardianReceipt();

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LocationStatus locationStatus;

	@Embedded
	private CapturedLocation location;

	/** 지금까지 다시 알린 횟수. 열람이 기록되면 멈춘다. */
	@Column(nullable = false)
	private int renotifyCount;

	protected HelpRequest() {
	}

	private HelpRequest(UUID linkId, UUID senderId, UUID guardianId, LocalDateTime sentAt,
			LocationStatus locationStatus, CapturedLocation location) {
		this.linkId = linkId;
		this.senderId = senderId;
		this.guardianId = guardianId;
		this.sentAt = sentAt;
		this.locationStatus = locationStatus;
		this.location = location;
	}

	/**
	 * 도움 요청을 보낸다. 위치를 아직 못 얻었으면 기다리지 않고 PENDING으로 먼저 보내고 나중에 붙인다.
	 *
	 * <p>위치 공유가 꺼져 있으면 좌표가 와도 저장하지 않고 NOT_SHARED로만 남긴다(도움 요청 API).
	 */
	public static HelpRequest send(UUID linkId, UUID senderId, UUID guardianId, LocalDateTime now,
			LocationStatus locationStatus, CapturedLocation location) {
		return new HelpRequest(linkId, senderId, guardianId, now, locationStatus,
				locationStatus == LocationStatus.ATTACHED ? location : null);
	}

	/**
	 * 나중에 얻은 위치를 붙인다. 아직 기다리는 중일 때만 가능하고, 한 번 정해지면 바꿀 수 없다(위치 붙이기 API).
	 *
	 * @param status ATTACHED 또는 끝내 못 얻었다면 UNAVAILABLE
	 */
	public void attachLocation(LocationStatus status, CapturedLocation location) {
		if (locationStatus != LocationStatus.PENDING) {
			throw new SonulimException(ErrorCode.LOCATION_ALREADY_SET, "이미 위치가 첨부된 요청이에요");
		}
		this.locationStatus = status;
		this.location = status == LocationStatus.ATTACHED ? location : null;
	}

	/** 좌표를 주소로 바꿔 채운다. 바꾸지 못하면 null로 남겨 둔다. */
	public void fillAddress(String address) {
		if (location != null) {
			location.fillAddress(address);
		}
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

	/**
	 * 보호자가 열어 봤다.
	 *
	 * @return 이번이 첫 열람이면 true — 재알림을 멈추고 당사자에게 '확인함'을 보낼 시점이다
	 */
	public boolean markOpened(LocalDateTime now) {
		return receipt().markOpened(now);
	}

	/** 아직 열어 보지 않았을 때만 다시 알린다. */
	public void countRenotify() {
		if (receipt().getOpenedAt() == null) {
			renotifyCount++;
		}
	}

	public boolean isOpened() {
		return receipt().getOpenedAt() != null;
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

	public LocalDateTime getSentAt() {
		return sentAt;
	}

	public LocalDateTime getGuardianDeliveredAt() {
		return receipt().getDeliveredAt();
	}

	public LocalDateTime getGuardianOpenedAt() {
		return receipt().getOpenedAt();
	}

	public LocationStatus getLocationStatus() {
		return locationStatus;
	}

	public CapturedLocation getLocation() {
		return location;
	}

	public int getRenotifyCount() {
		return renotifyCount;
	}
}
