package com.sonulim.domain.messaging.domain;

/**
 * 보낸 시점 신호 스냅샷에 박제되는 카테고리.
 *
 * <p>signal 도메인에도 같은 값이 생기겠지만 여기 것은 <b>그 시점 값을 복사해 굳힌 것</b>이라 별개다 —
 * 신호를 고치거나 지워도 과거 기록은 바뀌지 않아야 하고, messaging이 signal 패키지에 의존해서도 안 된다
 * (docs/architecture.md: 도메인 패키지는 서로 직접 의존하지 않는다).
 */
public enum SignalCategory {

	DAILY,
	OUTING,
	CHECK,
	EMERGENCY
}
