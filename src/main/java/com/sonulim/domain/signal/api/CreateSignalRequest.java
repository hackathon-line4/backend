package com.sonulim.domain.signal.api;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.application.CreateSignalCommand;
import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * name·category·rhythm은 source=CUSTOM일 때만 필수라 여기선 길이 등 무조건적인 제약만
 * 검사하고, "조건부 필수" 여부는 서비스가 source별로 검증한다.
 */
public record CreateSignalRequest(
		@NotNull SignalSource source,
		UUID templateId,
		@Size(max = 12) String name,
		String description,
		SignalCategory category,
		String icon,
		List<String> rhythm,
		UUID presetId) {

	public CreateSignalCommand toCommand() {
		return new CreateSignalCommand(source, templateId, name, description, category, icon, rhythm, presetId);
	}
}
