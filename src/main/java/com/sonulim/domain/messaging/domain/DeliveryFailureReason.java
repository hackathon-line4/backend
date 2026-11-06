package com.sonulim.domain.messaging.domain;

/**
 * delivery.status가 FAILED일 때 함께 저장하는 사유(공통 데이터 형식).
 */
public enum DeliveryFailureReason {

	/** 당사자 기기 미등록·로그아웃. */
	RECIPIENT_NO_DEVICE,
	PUSH_PROVIDER_ERROR,
	INTERNAL_ERROR
}
