package com.sonulim.domain.vibration.application;

import com.sonulim.domain.vibration.domain.VibrationIntensity;
import com.sonulim.domain.vibration.domain.VibrationLength;

// intensity가 null이면 기기가 세기 조절을 지원하지 않는 것
public record VibrationSettingsChange(VibrationIntensity intensity, VibrationLength length, int repeatCount) {
}
