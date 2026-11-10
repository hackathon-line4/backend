package com.sonulim.domain.history.api;

import java.time.LocalDateTime;
import java.util.UUID;

import com.sonulim.domain.history.domain.DeliveryState;
import com.sonulim.domain.history.domain.HistoryItem;
import com.sonulim.domain.history.domain.HistoryKind;
import com.sonulim.domain.history.domain.ResponseState;
import com.sonulim.domain.history.domain.SummaryStatus;

/**
 * 공통 데이터 형식의 HistoryItem. response는 응답이 없는 기록(전달 전·실패·연습·당사자 기록)에서 null이다.
 */
public record HistoryItemResponse(UUID id, HistoryKind kind, String title, String description, boolean isEmergency,
		LocalDateTime occurredAt, Delivery delivery, Response response, LocalDateTime openedAt,
		SummaryStatus summaryStatus, boolean canResend) {

	// 홈 요약(link, #81)의 recentActivities도 같은 모양으로 쓴다.
	public static HistoryItemResponse from(HistoryItem item) {
		Response response = item.responseStatus() == null
				? null
				: new Response(item.responseStatus(), item.respondedAt());
		return new HistoryItemResponse(item.id(), item.kind(), item.title(), item.description(), item.emergency(),
				item.occurredAt(), new Delivery(item.deliveryStatus(), item.deliveredAt(), item.failureReason()),
				response, item.openedAt(), item.summaryStatus(), item.canResend());
	}

	public record Delivery(DeliveryState status, LocalDateTime at, String failureReason) {
	}

	public record Response(ResponseState status, LocalDateTime at) {
	}
}
