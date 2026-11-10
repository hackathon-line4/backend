package com.sonulim.domain.link.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.sonulim.domain.history.api.HistoryItemResponse;
import com.sonulim.domain.history.domain.HistoryItem;
import com.sonulim.domain.link.api.LinkRequestResponse.Connection;
import com.sonulim.domain.link.application.HomeSummary;
import com.sonulim.domain.link.application.RecipientStatus;
import com.sonulim.domain.signal.api.SignalResponse;

/**
 * 홈 요약(S-4). favoriteSignals는 신호 API와, recentActivities는 기록 API와 같은 모양을 그대로 쓴다.
 */
public record HomeResponse(Recipient recipient, List<SignalResponse> favoriteSignals, Location location,
		List<HistoryItemResponse> recentActivities) {

	static HomeResponse from(HomeSummary summary) {
		return new HomeResponse(
				new Recipient(summary.recipient().id(), summary.recipient().name(), initial(summary.recipient().name()),
						Connection.from(summary.link()), Status.from(summary.status()),
						LastResponse.from(summary.lastResponse())),
				summary.favoriteSignals().stream().map(SignalResponse::from).toList(),
				Location.NOT_SHARED,
				summary.recentActivities().stream().map(HistoryItemResponse::from).toList());
	}

	// 이름의 첫 글자(명세 해석, Pending Decisions).
	private static String initial(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		String trimmed = name.strip();
		return trimmed.substring(0, trimmed.offsetByCodePoints(0, 1));
	}

	public record Recipient(UUID id, String name, String initial, Connection connection, Status status,
			LastResponse lastResponse) {
	}

	public record Status(RecipientStatus.Type type, UUID messageId, String signalName, LocalDateTime since,
			Long elapsedMinutes) {

		static Status from(RecipientStatus status) {
			return new Status(status.type(), status.messageId(), status.signalName(), status.since(),
					status.elapsedMinutes());
		}
	}

	public record LastResponse(UUID messageId, String signalName, LocalDateTime respondedAt) {

		static LastResponse from(HistoryItem item) {
			return item == null ? null : new LastResponse(item.id(), item.title(), item.respondedAt());
		}
	}

	/**
	 * 위치 공유 정책 확정 전이라 sharingEnabled: false, last: null 고정(명세가 허용).
	 */
	public record Location(boolean sharingEnabled, Object last) {

		static final Location NOT_SHARED = new Location(false, null);
	}
}
