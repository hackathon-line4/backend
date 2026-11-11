package com.sonulim.domain.link.application;

import java.util.List;

import com.sonulim.domain.account.application.UserSummary;
import com.sonulim.domain.history.domain.HistoryItem;
import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.signal.domain.Signal;

/**
 * 홈 요약(S-4) 한 화면에 필요한 값. 응답 모양으로 바꾸는 것은 api 계층이 한다.
 *
 * @param lastResponse 응답 시각이 가장 최근인 응답 받은 보호자 신호, 없으면 null
 */
public record HomeSummary(Link link, UserSummary recipient, RecipientStatus status, HistoryItem lastResponse,
		List<Signal> favoriteSignals, List<HistoryItem> recentActivities) {
}
