package com.sonulim.domain.signal.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.domain.Signal;

/**
 * 공통 데이터 형식 문서의 Signal 객체 모양 그대로.
 */
public record SignalResponse(
		UUID id,
		String source,
		UUID templateId,
		UUID presetId,
		String name,
		String description,
		String category,
		String icon,
		List<String> rhythm,
		long durationMs,
		boolean practiced,
		LocalDateTime practicedAt,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	public static SignalResponse from(Signal signal) {
		return new SignalResponse(
				signal.getId(),
				signal.getSource().name(),
				signal.getTemplateId(),
				signal.getPresetId(),
				signal.getName(),
				signal.getDescription(),
				signal.getCategory().name(),
				signal.getIcon(),
				signal.getRhythm().stream().map(Enum::name).toList(),
				signal.calculateDurationMs(),
				signal.isPracticed(),
				signal.getPracticedAt(),
				signal.getCreatedAt(),
				signal.getUpdatedAt());
	}
}
