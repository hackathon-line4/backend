package com.sonulim.domain.vibration.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.sonulim.global.BaseEntity;

/**
 * 계정 단위 진동 설정. 저장한 적이 있을 때만 행이 있다 — 응답의 updatedAt은 BaseEntity의 updatedAt.
 */
@Entity
@Table(name = "vibration_settings")
public class VibrationSettings extends BaseEntity {

	@Id
	private UUID userId;

	// null이면 기기가 세기 조절을 지원하지 않아 저장하지 않은 것
	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private VibrationIntensity intensity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private VibrationLength length;

	@Column(nullable = false)
	private int repeatCount;

	protected VibrationSettings() {
	}

	private VibrationSettings(UUID userId, VibrationIntensity intensity, VibrationLength length, int repeatCount) {
		this.userId = userId;
		this.intensity = intensity;
		this.length = length;
		this.repeatCount = repeatCount;
	}

	/**
	 * API 명세의 기본값: 보통 세기, 보통 길이, 2번 반복. 저장하지 않은 상태로 돌려주는 데 쓴다.
	 */
	public static VibrationSettings defaults(UUID userId) {
		return new VibrationSettings(userId, VibrationIntensity.NORMAL, VibrationLength.NORMAL, 2);
	}

	public UUID getUserId() {
		return userId;
	}

	public VibrationIntensity getIntensity() {
		return intensity;
	}

	public VibrationLength getLength() {
		return length;
	}

	public int getRepeatCount() {
		return repeatCount;
	}
}
