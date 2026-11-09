package com.sonulim.domain.messaging.domain;

/**
 * 신호 한 건의 종류. 연습(PRACTICE)은 응답을 받지 않는다 — response가 항상 null이다(공통 데이터 형식).
 */
public enum MessageKind {

	SIGNAL,
	PRACTICE
}
