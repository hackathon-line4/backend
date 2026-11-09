package com.sonulim.domain.vibration.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.sonulim.domain.vibration.application.VibrationSettingsChange;
import com.sonulim.domain.vibration.domain.VibrationIntensity;
import com.sonulim.domain.vibration.domain.VibrationLength;

// enum 값은 문자열로 받아 잘못된 값을 VALIDATION_FAILED(field)로 응답한다. intensity는 null 허용(세기 조절 미지원).
// 반복 범위 1~5는 기기 테스트 후 확정(docs/domains/vibration.md Pending Decisions).
public record VibrationSettingsRequest(
		@Pattern(regexp = "WEAK|NORMAL|STRONG", message = "세기는 WEAK, NORMAL, STRONG 중 하나여야 해요") String intensity,
		@NotNull @Pattern(regexp = "SHORT|NORMAL|LONG", message = "길이는 SHORT, NORMAL, LONG 중 하나여야 해요") String length,
		@NotNull @Min(value = 1, message = "반복은 1~5번이어야 해요") @Max(value = 5, message = "반복은 1~5번이어야 해요")
		Integer repeatCount) {

	VibrationSettingsChange toChange() {
		return new VibrationSettingsChange(intensity == null ? null : VibrationIntensity.valueOf(intensity),
				VibrationLength.valueOf(length), repeatCount);
	}
}
