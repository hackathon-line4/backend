package com.sonulim.domain.history.api;

import java.util.List;

import com.sonulim.domain.history.application.HistoryService.HistoryDay;

/**
 * 기록이 없는 날은 items: [].
 */
public record HistoryResponse(String date, boolean hasPrevDate, List<HistoryItemResponse> items) {

	static HistoryResponse from(HistoryDay day) {
		return new HistoryResponse(day.date().toString(), day.hasPrevDate(),
				day.items().stream().map(HistoryItemResponse::from).toList());
	}
}
