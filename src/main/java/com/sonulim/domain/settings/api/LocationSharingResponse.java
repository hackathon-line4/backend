package com.sonulim.domain.settings.api;

import java.time.LocalDateTime;

import com.sonulim.domain.settings.domain.LocationSharing;

public record LocationSharingResponse(boolean enabled, LocalDateTime updatedAt) {

	public static LocationSharingResponse from(LocationSharing locationSharing) {
		return new LocationSharingResponse(locationSharing.isEnabled(), locationSharing.getChangedAt());
	}
}
