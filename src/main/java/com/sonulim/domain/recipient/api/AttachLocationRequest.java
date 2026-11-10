package com.sonulim.domain.recipient.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.sonulim.domain.recipient.domain.CapturedLocation;
import com.sonulim.domain.recipient.domain.LocationStatus;

/**
 * 나중에 얻은 위치를 붙인다. 권한 거부·측위 실패면 좌표 없이 UNAVAILABLE만 보낸다(위치 붙이기 API).
 */
public record AttachLocationRequest(
		@NotNull(message = "위치 상태가 필요해요") LocationStatus locationStatus,
		@Valid Location location) {

	public record Location(
			@NotNull(message = "위도가 필요해요") BigDecimal latitude,
			@NotNull(message = "경도가 필요해요") BigDecimal longitude,
			Integer accuracyM,
			@NotNull(message = "위치를 잰 시각이 필요해요") LocalDateTime capturedAt) {
	}

	CapturedLocation toCapturedLocation() {
		return location == null ? null
				: new CapturedLocation(location.latitude(), location.longitude(), location.accuracyM(),
						location.capturedAt());
	}
}
