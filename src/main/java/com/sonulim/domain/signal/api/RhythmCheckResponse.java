package com.sonulim.domain.signal.api;

import java.util.UUID;

import com.sonulim.domain.signal.application.RhythmCheckResult;

public record RhythmCheckResponse(boolean valid, long durationMs, ConflictSignal conflictSignal) {

	public record ConflictSignal(UUID id, String name) {
	}

	public static RhythmCheckResponse from(RhythmCheckResult result) {
		ConflictSignal conflict = result.conflictSignal() == null ? null
				: new ConflictSignal(result.conflictSignal().id(), result.conflictSignal().name());
		return new RhythmCheckResponse(result.valid(), result.durationMs(), conflict);
	}
}
