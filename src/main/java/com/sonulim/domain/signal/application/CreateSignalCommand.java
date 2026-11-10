package com.sonulim.domain.signal.application;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalSource;

/**
 * POST /signals 생성 요청 — source로 CUSTOM(M-3)과 TEMPLATE(M-2)를 가른다.
 * templateId는 TEMPLATE일 때 필수, name·category·rhythm은 CUSTOM일 때 필수(서비스가
 * source별로 검증한다 — 단순 @NotNull로는 조건부 필수를 표현할 수 없다).
 */
public record CreateSignalCommand(
		SignalSource source,
		UUID templateId,
		String name,
		String description,
		SignalCategory category,
		String icon,
		List<String> rhythm) {
}
