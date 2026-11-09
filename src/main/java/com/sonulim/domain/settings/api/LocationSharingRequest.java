package com.sonulim.domain.settings.api;

import jakarta.validation.constraints.NotNull;

public record LocationSharingRequest(@NotNull Boolean enabled) {
}
