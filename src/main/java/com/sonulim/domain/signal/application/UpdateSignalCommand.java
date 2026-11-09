package com.sonulim.domain.signal.application;

import java.util.List;

import com.sonulim.domain.signal.domain.SignalCategory;

/**
 * PATCH 부분 수정 — null인 필드는 그대로 둔다.
 */
public record UpdateSignalCommand(
		String name,
		String description,
		SignalCategory category,
		String icon,
		List<String> rhythm) {
}
