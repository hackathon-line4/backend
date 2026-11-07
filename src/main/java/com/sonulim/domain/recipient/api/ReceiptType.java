package com.sonulim.domain.recipient.api;

/**
 * 보호자 앱이 보내는 보고 종류(도착·열람 기록 API).
 * DELIVERED는 푸시 수신 핸들러에서, OPENED는 알림이나 기록을 눌러 열었을 때 보낸다.
 */
public enum ReceiptType {

	DELIVERED,
	OPENED
}
