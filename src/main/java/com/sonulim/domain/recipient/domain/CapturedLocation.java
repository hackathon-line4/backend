package com.sonulim.domain.recipient.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 기기가 잰 위치 한 건. WGS84 십진 도, 소수점 6자리(공통 데이터 형식).
 *
 * <p>좌표는 double 대신 BigDecimal로 둔다 — 소수점 6자리를 정확히 보존해야 하고, double은 반올림 오차가 남는다.
 */
@Embeddable
public class CapturedLocation {

	@Column(precision = 9, scale = 6)
	private BigDecimal latitude;

	@Column(precision = 9, scale = 6)
	private BigDecimal longitude;

	private Integer accuracyM;

	/** 서버가 좌표를 주소로 바꿔 채운다. 바꾸지 못하면 null로 둔다. */
	private String address;

	/** 기기가 위치를 잰 시각. */
	private LocalDateTime capturedAt;

	protected CapturedLocation() {
	}

	public CapturedLocation(BigDecimal latitude, BigDecimal longitude, Integer accuracyM, LocalDateTime capturedAt) {
		this.latitude = latitude;
		this.longitude = longitude;
		this.accuracyM = accuracyM;
		this.capturedAt = capturedAt;
	}

	void fillAddress(String address) {
		this.address = address;
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
