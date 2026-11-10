package com.sonulim.domain.history.domain;

/**
 * 기기 전달(delivery.status). 값은 공통 데이터 형식의 상태 정의 그대로다.
 */
public enum DeliveryState {
	REQUESTED,
	FAILED,
	DELIVERED,
	UNCONFIRMED
}
