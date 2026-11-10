package com.sonulim.domain.history.api;

import java.util.List;

import com.sonulim.domain.history.application.HistoryService.HistoryMonth;

/**
 * 기록이 한 건도 없는 날은 days에 없다.
 */
public record HistoryCalendarResponse(String month, List<Day> days) {

	static HistoryCalendarResponse from(HistoryMonth month) {
		return new HistoryCalendarResponse(month.month().toString(), month.days().stream()
				.map(day -> new Day(day.date().toString(), day.count(), day.hasNoResponse()))
				.toList());
	}

	public record Day(String date, int count, boolean hasNoResponse) {
	}
}
