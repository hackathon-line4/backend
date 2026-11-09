package com.sonulim.domain.signal.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record RhythmCheckRequest(@NotNull List<String> rhythm, UUID excludeSignalId) {
}
