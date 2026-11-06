package com.sonulim.domain.signal.api;

import com.sonulim.domain.signal.application.RhythmCheckResult;

public record RhythmCheckResponse(boolean duplicate, String conflictingSignalName) {

	public static RhythmCheckResponse from(RhythmCheckResult result) {
		return new RhythmCheckResponse(result.duplicate(), result.conflictingSignalName());
	}
}
