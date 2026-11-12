package com.sonulim.global.push;

import java.util.List;
import java.util.UUID;

/**
 * FCM 발송이 읽는 기기 푸시 토큰 창구. 구현은 기기를 관리하는 account 도메인이 제공한다
 * ({@code domain.account.application.DevicePushTokenRegistry}) — global은 도메인에 의존하지 않는다.
 *
 * <p>커밋이 끝난 뒤에도 불릴 수 있어서, 구현은 호출한 쪽 트랜잭션에 기대지 않고 자기 트랜잭션에서 실행해야 한다.
 */
public interface PushTokenRegistry {

	/** 사용자가 푸시를 받을 기기 토큰들. 등록된 기기가 없으면 빈 목록. */
	List<String> tokensOf(UUID userId);

	/** FCM이 더 이상 유효하지 않다고 알려 준 토큰(앱 삭제 등)을 지운다. */
	void discard(String pushToken);
}
