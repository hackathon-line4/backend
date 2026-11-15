package com.sonulim.domain.link.api;

import java.time.LocalDateTime;
import java.util.UUID;

import com.sonulim.domain.link.domain.ConnectionCheck;
import com.sonulim.domain.link.domain.ConnectionCheckStatus;

public record ConnectionCheckResponse(UUID id, ConnectionCheckStatus status, UUID requestedBy,
		LocalDateTime requestedAt, LocalDateTime confirmedAt, LocalDateTime timeoutAt) {

	public static ConnectionCheckResponse from(ConnectionCheck check) {
		return new ConnectionCheckResponse(check.getId(), check.getStatus(), check.getRequestedBy(),
				check.getRequestedAt(), check.getConfirmedAt(), check.getTimeoutAt());
	}
}
