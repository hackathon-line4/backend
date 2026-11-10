package com.sonulim.domain.history.domain;

/**
 * 터치 응답(response.status). 값은 공통 데이터 형식의 상태 정의 그대로다. 응답이 없는 기록(전달 전·실패·연습·당사자 기록)은 null.
 */
public enum ResponseState {
	WAITING,
	RESPONDED,
	NO_RESPONSE
}
