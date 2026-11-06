package com.sonulim.domain.messaging.domain;

/**
 * 기록 상세 화면의 타임라인 한 줄([전송] 신호 기록 상세).
 * 예) 보냄 6:02 → 기기에 도착 6:02 → 진동 재생됨 3번 반복 6:02 → 화면 터치 없음 6:12
 */
public enum MessageEventStep {

	SENT,
	FAILED,
	DELIVERED,
	UNCONFIRMED,
	/** meta에 누적 재생 횟수(count)를 싣는다. */
	PLAYED,
	RESPONDED,
	NO_RESPONSE,
	WAIT_EXTENDED,
	EMERGENCY_WARNING
}
