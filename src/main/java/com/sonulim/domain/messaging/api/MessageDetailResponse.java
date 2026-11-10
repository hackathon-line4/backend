package com.sonulim.domain.messaging.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.sonulim.domain.messaging.domain.MessageEvent;
import com.sonulim.domain.messaging.domain.MessageEventStep;

/**
 * 기록 상세(H-2, S-3). 보냄 → 기기에 도착 → 진동 재생됨 → 화면 터치 없음 순서로 시각을 보여 준다.
 *
 * @param relatedHelpRequest 도움 요청 뒤 일정 시간 안에 보낸 신호면 그 요약. 기준 시간이 미확정이라 지금은 항상 null
 *                           (docs/domains/messaging.md의 Pending Decisions)
 * @param canResend          원래 신호가 삭제되지 않았으면 true
 */
public record MessageDetailResponse(MessageResponse message, List<TimelineEntry> timeline,
		Object relatedHelpRequest, boolean canExtendWait, boolean canResend) {

	/** count는 PLAYED 단계에서만 채워진다. */
	public record TimelineEntry(MessageEventStep step, LocalDateTime at, Integer count) {

		static TimelineEntry from(MessageEvent event) {
			Map<String, Object> meta = event.getMeta();
			Integer count = meta == null || !(meta.get("count") instanceof Number number) ? null : number.intValue();
			return new TimelineEntry(event.getStep(), event.getAt(), count);
		}
	}

	public static MessageDetailResponse of(MessageResponse message, List<MessageEvent> timeline,
			boolean canExtendWait, boolean canResend) {
		return new MessageDetailResponse(message, timeline.stream().map(TimelineEntry::from).toList(),
				null, canExtendWait, canResend);
	}
}
