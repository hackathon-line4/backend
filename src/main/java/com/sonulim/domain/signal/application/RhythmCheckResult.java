package com.sonulim.domain.signal.application;

import java.util.UUID;

public record RhythmCheckResult(boolean valid, long durationMs, ConflictSignal conflictSignal) {

	public record ConflictSignal(UUID id, String name) {
	}
}
