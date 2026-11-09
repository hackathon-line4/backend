package com.sonulim.domain.signal.api;

import java.util.List;

import com.sonulim.domain.signal.application.UpdateSignalCommand;
import com.sonulim.domain.signal.domain.SignalCategory;
import jakarta.validation.constraints.Size;

/**
 * PATCH 부분 수정 — 보낸 필드만 바뀐다(null은 그대로 둔다는 뜻).
 */
public record UpdateSignalRequest(
		@Size(min = 1, max = 12) String name,
		String description,
		SignalCategory category,
		String icon,
		List<String> rhythm) {

	public UpdateSignalCommand toCommand() {
		return new UpdateSignalCommand(name, description, category, icon, rhythm);
	}
}
