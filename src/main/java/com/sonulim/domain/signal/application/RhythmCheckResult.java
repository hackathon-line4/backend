package com.sonulim.domain.signal.application;

public record RhythmCheckResult(boolean duplicate, String conflictingSignalName) {
}
