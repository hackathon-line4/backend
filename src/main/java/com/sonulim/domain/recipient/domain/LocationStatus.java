package com.sonulim.domain.recipient.domain;

/**
 * 도움 요청에 위치가 붙었는지(공통 데이터 형식).
 */
public enum LocationStatus {

	/** 위치 있음. */
	ATTACHED,
	/** 아직 못 얻어서 나중에 붙일 예정 — 기다리지 않고 요청을 먼저 보낸다. */
	PENDING,
	/** 당사자가 위치 공유를 꺼 둠. 위치가 와도 저장하지 않는다. */
	NOT_SHARED,
	/** 권한 없음·측위 실패. */
	UNAVAILABLE
}
