package com.sonulim.domain.recipient.api;

import java.time.LocalDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.sonulim.domain.recipient.domain.CapturedLocation;
import com.sonulim.domain.recipient.domain.LocationStatus;

/**
 * 위치를 아직 못 얻었으면 기다리지 말고 PENDING으로 먼저 보낸다 — 나중에 위치 붙이기로 채운다(도움 요청 API).
 */
public record CreateHelpRequestRequest(
		@NotNull(message = "보낸 시각이 필요해요") LocalDateTime sentAt,
		@NotNull(message = "위치 상태가 필요해요") LocationStatus locationStatus,
		@Valid AttachLocationRequest.Location location) {

	CapturedLocation toCapturedLocation() {
		return location == null ? null
				: new CapturedLocation(location.latitude(), location.longitude(), location.accuracyM(),
						location.capturedAt());
	}
}
