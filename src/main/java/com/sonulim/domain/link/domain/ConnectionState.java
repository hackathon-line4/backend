package com.sonulim.domain.link.domain;

/**
 * 연결의 connection.state(Notion "공통 데이터 형식"). 홈의 연결 배지에 쓴다.
 */
public enum ConnectionState {
	/** 연결됨 */
	CONFIRMED,
	/** 연결 확인 안 됨 */
	UNCONFIRMED,
	/** 확인한 적 없음 */
	UNKNOWN
}
