package com.sonulim.domain.location.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.sonulim.global.BaseEntity;

/**
 * 당사자의 마지막 위치. <b>당사자당 한 건만</b> 두고 새 위치가 오면 덮어쓴다 —
 * 이동 경로나 과거 위치를 쌓지 않는 것이 이 서비스의 위치 정책이다(마지막 위치 조회 API).
 *
 * <p>그래서 당사자 ID가 그대로 기본 키다. 위치 공유를 끄면 이 행을 지운다.
 *
 * <p>좌표는 double 대신 BigDecimal이다 — 소수점 6자리가 지도에 찍히는 값이라 반올림 오차가 남으면 안 된다.
 */
@Entity
@Table(name = "last_locations")
public class LastLocation extends BaseEntity {

	@Id
	private UUID recipientId;

	@Column(nullable = false, precision = 9, scale = 6)
	private BigDecimal latitude;

	@Column(nullable = false, precision = 9, scale = 6)
	private BigDecimal longitude;

	private Integer accuracyM;

	/** 서버가 좌표를 주소로 바꿔 채운다. 바꾸지 못하면 null로 둔다. */
	private String address;

	/** 기기가 위치를 잰 시각. 서버가 받은 시각이 아니다. */
	@Column(nullable = false)
	private LocalDateTime capturedAt;

	protected LastLocation() {
	}

	private LastLocation(UUID recipientId, BigDecimal latitude, BigDecimal longitude, Integer accuracyM,
			LocalDateTime capturedAt) {
		this.recipientId = recipientId;
		overwriteWith(latitude, longitude, accuracyM, capturedAt);
	}

	public static LastLocation of(UUID recipientId, BigDecimal latitude, BigDecimal longitude, Integer accuracyM,
			LocalDateTime capturedAt) {
		return new LastLocation(recipientId, latitude, longitude, accuracyM, capturedAt);
	}

	/**
	 * 새 위치로 덮어쓴다. 주소는 좌표가 바뀌었으니 지우고, 변환이 끝나면 다시 채운다.
	 */
	public void overwriteWith(BigDecimal latitude, BigDecimal longitude, Integer accuracyM,
			LocalDateTime capturedAt) {
		this.latitude = latitude;
		this.longitude = longitude;
		this.accuracyM = accuracyM;
		this.capturedAt = capturedAt;
		this.address = null;
	}

	public void fillAddress(String address) {
		this.address = address;
	}

	public UUID getRecipientId() {
		return recipientId;
	}

	public BigDecimal getLatitude() {
		return latitude;
	}

	public BigDecimal getLongitude() {
		return longitude;
	}

	public Integer getAccuracyM() {
		return accuracyM;
	}

	public String getAddress() {
		return address;
	}

	public LocalDateTime getCapturedAt() {
		return capturedAt;
	}
}
