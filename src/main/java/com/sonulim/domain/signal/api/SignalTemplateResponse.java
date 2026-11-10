package com.sonulim.domain.signal.api;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.application.SignalTemplateView;

public record SignalTemplateResponse(
		UUID id,
		String name,
		String description,
		String category,
		String icon,
		List<String> rhythm,
		List<Integer> pulses,
		long durationMs,
		boolean alreadyAdded) {

	public static SignalTemplateResponse from(SignalTemplateView view) {
		var template = view.template();
		return new SignalTemplateResponse(
				template.getId(),
				template.getName(),
				template.getDescription(),
				template.getCategory().name(),
				template.getIcon(),
				template.getRhythm() == null ? null : template.getRhythm().stream().map(Enum::name).toList(),
				template.getPulses(),
				template.getPulses().stream().mapToLong(Integer::longValue).sum(),
				view.alreadyAdded());
	}
}
