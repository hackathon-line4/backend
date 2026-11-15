package com.sonulim.global.signalpractice;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 연습 신호(PRACTICE) ack 때 그 신호의 연습 완료 상태를 갱신하기 위한 계약.
 * DefaultSignalSeeder·LinkAccessGuard와 같은 패턴 — signal이 정의·구현하고, messaging은
 * 이 인터페이스만 의존한다(docs/architecture.md "도메인 간 의존 방향": 도메인끼리 직접
 * 의존하지 않고 global을 통해 연동).
 */
public interface SignalPracticeTracker {

	void markPracticed(UUID signalId, LocalDateTime at);
}
