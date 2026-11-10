package com.sonulim.domain.history.domain;

/**
 * 배지용 요약(HistoryItem.summaryStatus). 값은 공통 데이터 형식 그대로, 어떤 상태가 어떤 값이 되는지는 명세 해석이다
 * (docs/domains/history.md Pending Decisions).
 */
public enum SummaryStatus {
	SENDING,
	FAILED,
	UNCONFIRMED,
	WAITING,
	DELIVERED_ONLY,
	RESPONDED,
	NO_RESPONSE
}
