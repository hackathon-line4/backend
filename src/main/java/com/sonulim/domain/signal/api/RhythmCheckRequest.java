package com.sonulim.domain.signal.api;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record RhythmCheckRequest(@NotNull List<String> rhythm) {
}
