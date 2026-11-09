package com.sonulim.domain.messaging.domain;

/**
 * 기기 전달 상태. '전달 완료'는 당사자 앱의 ack로만 바뀐다 — 푸시 발송 성공만으로는 바꾸지 않는다(공통 규칙).
 */
public enum DeliveryStatus {

	/** 기록을 만들고 푸시를 보냈음. 아직 ack 없음. */
	REQUESTED,
	/** 푸시를 보낼 수 없음. 더 진행되지 않고, 다시 보내기는 새 기록을 만든다. */
	FAILED,
	/** 당사자 앱이 ack를 보냄. */
	DELIVERED,
	/** ack 판정 시간 안에 ack가 없음. 늦게 ack가 오면 DELIVERED로 바뀐다. */
	UNCONFIRMED
}
