package com.sonulim.global.push;

/**
 * 푸시 종류와 전달 방식(서버 내부 처리 문서의 푸시 알림 종류 표).
 *
 * <p>{@code DATA}는 앱이 직접 리듬을 울려야 해서 표시 없이 데이터만 보내는 것이고,
 * {@code SILENT}는 앱이 조용히 API를 호출하게 하는 것이다. 사용자에게 보이지 않는다는 점은 같지만
 * 받는 쪽이 하는 일이 달라 구분해 둔다.
 */
public enum PushType {

	/** 보호자에게 — 연결 요청이 왔다. */
	LINK_REQUEST(Delivery.NORMAL),
	/** 당사자에게 — 연결 요청이 승인·거절·만료됐다. */
	LINK_RESULT(Delivery.NORMAL),
	/** 당사자에게 — 신호가 왔다. 앱이 리듬을 직접 울린다. */
	SIGNAL(Delivery.DATA),
	/** 당사자에게 — 알려주기(연습). */
	PRACTICE(Delivery.DATA),
	/** 보호자에게 — 당사자가 화면을 터치했다. */
	TOUCH_RESPONSE(Delivery.NORMAL),
	/** 보호자에게 — 대기 시간이 끝나도록 응답이 없었다. */
	NO_RESPONSE(Delivery.NORMAL),
	/** 보호자에게 — 당사자가 불렀다. */
	RECIPIENT_SIGNAL(Delivery.HIGH),
	/** 보호자에게 — 도움 요청. 가장 강하게 알리고 끌 수 없다. */
	HELP_REQUEST(Delivery.CRITICAL),
	/** 당사자에게 — 보호자가 도움 요청을 열어 봤다. '확인함' 진동만 울린다. */
	HELP_CONFIRMED(Delivery.DATA),
	/** 양쪽에 — 연결 확인. 받으면 ack API를 부른다. */
	CONNECTION_CHECK(Delivery.SILENT),
	/** 당사자에게 — 위치를 보내 달라. 받으면 위치 보내기 API를 부른다. */
	LOCATION_REQUEST(Delivery.SILENT);

	private final Delivery delivery;

	PushType(Delivery delivery) {
		this.delivery = delivery;
	}

	public Delivery delivery() {
		return delivery;
	}

	/**
	 * 알림 설정으로 끌 수 있는지. 도움 요청은 사용자가 끌 수 없다(서버 내부 처리: "항상").
	 */
	public boolean isAlwaysDelivered() {
		return this == HELP_REQUEST;
	}

	public enum Delivery {
		/** 보통 알림. */
		NORMAL,
		/** 눈에 띄게. */
		HIGH,
		/** 소리 + 진동 + 전체 화면, 방해 금지 무시. */
		CRITICAL,
		/** 표시 없이 데이터만 — 앱이 받아서 직접 울린다. */
		DATA,
		/** 조용히 — 앱이 받아서 API를 부른다. */
		SILENT
	}
}
