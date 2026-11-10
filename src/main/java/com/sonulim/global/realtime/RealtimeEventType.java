package com.sonulim.global.realtime;

/**
 * SSE 이벤트 종류. Notion API 명세 `GET /api/v1/events`의 이벤트 명세 표 그대로다 — 새 이벤트는 명세에 먼저 추가한다.
 * global은 도메인에 의존하지 않으므로 이벤트 이름만 둔다. data의 모양은 각 도메인이 정한다.
 */
public enum RealtimeEventType {

	MESSAGE_CREATED("message.created"),
	MESSAGE_UPDATED("message.updated"),
	RECIPIENT_SIGNAL_CREATED("recipient_signal.created"),
	HELP_REQUEST_CREATED("help_request.created"),
	HELP_REQUEST_UPDATED("help_request.updated"),
	LINK_REQUEST_CREATED("link_request.created"),
	LINK_REQUEST_UPDATED("link_request.updated"),
	LINK_UPDATED("link.updated"),
	CONNECTION_CHECK_REQUESTED("connection_check.requested"),
	CONNECTION_CHECK_UPDATED("connection_check.updated"),
	LOCATION_REQUEST_CREATED("location_request.created"),
	LOCATION_REQUEST_UPDATED("location_request.updated"),
	LOCATION_UPDATED("location.updated"),
	SETTINGS_UPDATED("settings.updated");

	private final String eventName;

	RealtimeEventType(String eventName) {
		this.eventName = eventName;
	}

	/**
	 * SSE의 `event:` 줄에 쓰는 값.
	 */
	public String eventName() {
		return eventName;
	}
}
