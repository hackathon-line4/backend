package com.sonulim.domain.location.domain;

/**
 * 보호자가 보낸 위치 새로고침 요청의 상태(위치 새로고침 요청 API).
 */
public enum LocationRequestStatus {

	/** 당사자 기기에 조용한 푸시를 보내고 기다리는 중. */
	PENDING,
	/** 당사자가 위치를 보내 왔다. */
	FULFILLED,
	/** 당사자 기기가 위치를 얻지 못했거나 공유가 꺼져 있었다. */
	FAILED,
	/** 제한 시간 안에 아무 응답이 없었다. */
	TIMED_OUT
}
