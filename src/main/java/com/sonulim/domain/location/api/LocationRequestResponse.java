package com.sonulim.domain.location.api;

import java.time.LocalDateTime;
import java.util.UUID;

import com.sonulim.domain.location.domain.LocationFailureReason;
import com.sonulim.domain.location.domain.LocationRequest;
import com.sonulim.domain.location.domain.LocationRequestStatus;

public record LocationRequestResponse(UUID id, LocationRequestStatus status, LocalDateTime requestedAt,
		LocalDateTime timeoutAt, LocationFailureReason failureReason) {

	public static LocationRequestResponse from(LocationRequest request) {
		return new LocationRequestResponse(request.getId(), request.getStatus(), request.getRequestedAt(),
				request.getTimeoutAt(), request.getFailureReason());
	}
}
