package com.sonulim.domain.signal.api;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.application.RhythmPresetView;
import com.sonulim.domain.signal.domain.RhythmBeat;

public record RhythmPresetResponse(UUID id, List<String> rhythm, long durationMs, UsedBy usedBy) {

	public record UsedBy(UUID signalId, String name) {
	}

	public static RhythmPresetResponse from(RhythmPresetView view) {
		var preset = view.preset();
		UsedBy usedBy = view.usedBy() == null ? null : new UsedBy(view.usedBy().getId(), view.usedBy().getName());
		return new RhythmPresetResponse(
				preset.getId(),
				preset.getRhythm().stream().map(Enum::name).toList(),
				RhythmBeat.totalDurationMs(preset.getRhythm()),
				usedBy);
	}
}
