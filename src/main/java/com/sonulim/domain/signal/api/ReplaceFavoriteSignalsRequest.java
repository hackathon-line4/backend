package com.sonulim.domain.signal.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ReplaceFavoriteSignalsRequest(@NotNull List<UUID> signalIds) {
}
