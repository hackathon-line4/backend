package com.sonulim.domain.history.domain;

/**
 * 기록 목록 한 줄의 종류(Notion "공통 데이터 형식" HistoryItem.kind). id는 kind에 따라 messageId / recipientSignalId /
 * helpRequestId다.
 */
public enum HistoryKind {
	/** 보호자가 보낸 신호 */
	GUARDIAN_SIGNAL,
	/** 당사자가 보낸 신호(보호자 부르기) */
	RECIPIENT_SIGNAL,
	/** 도움 요청 */
	HELP_REQUEST,
	/** 연습(알려주기) */
	PRACTICE
}
