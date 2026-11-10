package com.sonulim.domain.history.application;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.history.domain.HistoryItem;
import com.sonulim.domain.history.domain.HistoryRecords;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 신호 기록 목록·기록 있는 날짜(H-1). 보호자만.
 *
 * <ul>
 *   <li>지금은 기록의 guardianId로 본인 기록만 고른다. 이 브랜치에 Link가 없어서 없는 연결·남의 연결도 404가 아니라 빈 목록이
 *       된다 — Link가 dev에 합쳐진 뒤 연결 존재 확인(404)과 ENDED 검사를 반드시 추가한다(docs/domains/history.md 후속 작업).</li>
 *   <li>하루는 tz 기준 00:00~24:00. 저장 시각은 UTC라 그 범위를 UTC로 바꿔 읽는다. 응답 시각은 tz와 관계없이 +09:00(공통 규칙).</li>
 * </ul>
 */
@Service
public class HistoryService {

	static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

	private final HistoryRecords historyRecords;
	private final Clock clock;

	public HistoryService(HistoryRecords historyRecords, Clock clock) {
		this.historyRecords = historyRecords;
		this.clock = clock;
	}

	/**
	 * 고른 날짜(생략하면 오늘)의 기록을 최신순으로 전부(페이지 나눔 없음, 명세). 오늘보다 뒤 날짜는 400.
	 * hasPrevDate는 그 날짜 전에 기록이 하나라도 있는지 — filter와 관계없다(명세 해석, Pending Decisions).
	 */
	public HistoryDay list(AuthenticatedUser user, String linkId, String date, String filter, String tz) {
		RoleRequirement.require(user, Role.GUARDIAN);
		UUID link = parseLinkId(linkId);
		ZoneId zone = parseZone(tz);
		HistoryFilter historyFilter = HistoryFilter.parse(filter);
		LocalDate today = clock.instant().atZone(zone).toLocalDate();
		LocalDate day = parseDate(date, today);
		if (day.isAfter(today)) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED, "오늘 이후 날짜의 기록은 볼 수 없어요", "date");
		}

		LocalDateTime from = toUtc(day, zone);
		List<HistoryItem> items = historyRecords.findByGuardian(link, user.userId(), from, toUtc(day.plusDays(1), zone))
				.stream()
				.filter(historyFilter::accepts)
				.sorted(Comparator.comparing(HistoryItem::occurredAt).reversed())
				.toList();
		return new HistoryDay(day, historyRecords.existsBefore(link, user.userId(), from), items);
	}

	/**
	 * 그 달에서 기록이 있는 날만 날짜순으로(명세). 날짜는 tz 기준이다.
	 */
	public HistoryMonth calendar(AuthenticatedUser user, String linkId, String month, String tz) {
		RoleRequirement.require(user, Role.GUARDIAN);
		UUID link = parseLinkId(linkId);
		ZoneId zone = parseZone(tz);
		YearMonth yearMonth = parseMonth(month);

		Map<LocalDate, List<HistoryItem>> byDay = new TreeMap<>();
		historyRecords.findByGuardian(link, user.userId(), toUtc(yearMonth.atDay(1), zone),
						toUtc(yearMonth.plusMonths(1).atDay(1), zone))
				.forEach(item -> byDay.computeIfAbsent(localDate(item.occurredAt(), zone), key -> new ArrayList<>())
						.add(item));
		List<CalendarDay> days = byDay.entrySet().stream()
				.map(entry -> new CalendarDay(entry.getKey(), entry.getValue().size(),
						entry.getValue().stream().anyMatch(HistoryItem::isNoResponse)))
				.toList();
		return new HistoryMonth(yearMonth, days);
	}

	private static LocalDateTime toUtc(LocalDate day, ZoneId zone) {
		return day.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
	}

	private static LocalDate localDate(LocalDateTime utc, ZoneId zone) {
		return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate();
	}

	// Link가 없는 이 브랜치에서는 형식이 틀린 id만 404로 걸러진다.
	private static UUID parseLinkId(String linkId) {
		try {
			return UUID.fromString(linkId);
		} catch (IllegalArgumentException exception) {
			throw new SonulimException(ErrorCode.NOT_FOUND, "연결을 찾을 수 없어요");
		}
	}

	private static ZoneId parseZone(String tz) {
		if (tz == null || tz.isBlank()) {
			return DEFAULT_ZONE;
		}
		try {
			return ZoneId.of(tz.trim());
		} catch (DateTimeException exception) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED, "tz는 IANA 시간대여야 해요(예: Asia/Seoul)", "tz");
		}
	}

	private static LocalDate parseDate(String date, LocalDate today) {
		if (date == null || date.isBlank()) {
			return today;
		}
		try {
			return LocalDate.parse(date.trim());
		} catch (DateTimeParseException exception) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED, "date는 YYYY-MM-DD 형식이어야 해요", "date");
		}
	}

	private static YearMonth parseMonth(String month) {
		if (month == null || month.isBlank()) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED, "month를 입력해 주세요", "month");
		}
		try {
			return YearMonth.parse(month.trim());
		} catch (DateTimeParseException exception) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED, "month는 YYYY-MM 형식이어야 해요", "month");
		}
	}

	private enum HistoryFilter {
		ALL,
		NO_RESPONSE;

		static HistoryFilter parse(String filter) {
			if (filter == null || filter.isBlank()) {
				return ALL;
			}
			try {
				return valueOf(filter.trim());
			} catch (IllegalArgumentException exception) {
				throw SonulimException.forField(ErrorCode.VALIDATION_FAILED, "filter는 ALL 또는 NO_RESPONSE여야 해요",
						"filter");
			}
		}

		boolean accepts(HistoryItem item) {
			return this == ALL || item.isNoResponse();
		}
	}

	public record HistoryDay(LocalDate date, boolean hasPrevDate, List<HistoryItem> items) {
	}

	public record HistoryMonth(YearMonth month, List<CalendarDay> days) {
	}

	public record CalendarDay(LocalDate date, int count, boolean hasNoResponse) {
	}
}
