package com.sonulim.domain.vibration.api;

import java.time.LocalDateTime;

import com.sonulim.domain.vibration.domain.VibrationIntensity;
import com.sonulim.domain.vibration.domain.VibrationLength;
import com.sonulim.domain.vibration.domain.VibrationSettings;

public record VibrationSettingsResponse(VibrationIntensity intensity, VibrationLength length, int repeatCount,
		LocalDateTime updatedAt) {

	public static VibrationSettingsResponse from(VibrationSettings settings) {
		// 저장 전 기본값은 영속 상태가 아니라 updatedAt이 null이다.
		return new VibrationSettingsResponse(settings.getIntensity(), settings.getLength(), settings.getRepeatCount(),
				settings.getUpdatedAt());
	}
}
