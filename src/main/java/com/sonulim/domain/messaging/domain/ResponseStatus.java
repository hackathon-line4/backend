package com.sonulim.domain.messaging.domain;

/**
 * 터치 응답 상태. 기기 전달 전·전송 실패·연습 신호에서는 null이다(공통 데이터 형식).
 */
public enum ResponseStatus {

	/** 기기 전달 후 waitUntil 전. */
	WAITING,
	/** 당사자의 터치가 서버에 기록됨. */
	RESPONDED,
	/** waitUntil까지 터치 없음. '더 기다리기'로 WAITING으로 되돌릴 수 있다. */
	NO_RESPONSE
}
