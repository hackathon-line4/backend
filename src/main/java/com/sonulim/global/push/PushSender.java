package com.sonulim.global.push;

/**
 * 푸시 발송. 각 도메인은 자기 이벤트를 여기로 넘기기만 하고, 기기 토큰 조회나 FCM 연동은 알지 못한다
 * (docs/architecture.md: 전달 메커니즘은 global에 둔다).
 *
 * <p>FCM 자격 증명이 있으면 {@link FcmPushSender}, 없는 local/test에서는 {@link LoggingPushSender}가 쓰인다.
 * 자격 증명 없는 prod에는 구현체가 없어 기동 자체가 실패한다 — 푸시가 조용히 안 나가는 일이 없도록 막아 둔다.
 *
 * <p>발송 실패가 호출한 쪽의 작업을 되돌려서는 안 된다. 신호 기록은 남고 전달만 실패하는 것이
 * 정상 상태이기 때문이다(공통 규칙: 전송 실패도 기록으로 남긴다).
 */
public interface PushSender {

	void send(PushMessage message);
}
