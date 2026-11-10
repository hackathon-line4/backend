package com.sonulim.domain.location.domain;

/**
 * 위치를 받지 못한 이유. PERMISSION_DENIED·POSITION_UNAVAILABLE는 당사자 기기가 알려 주고,
 * SHARING_OFF는 서버가 판단한다(위치 보내기 API).
 */
public enum LocationFailureReason {

	PERMISSION_DENIED,
	POSITION_UNAVAILABLE,
	SHARING_OFF
}
