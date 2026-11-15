package com.sonulim.global.signalpractice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 연습 신호(PRACTICE) ack 때 그 신호의 연습 완료 상태를 갱신하기 위한 계약.
 * DefaultSignalSeeder·LinkAccessGuard와 같은 패턴 — signal이 정의·구현하고, messaging은
 * 이 인터페이스만 의존한다(docs/architecture.md "도메인 간 의존 방향": 도메인끼리 직접
 * 의존하지 않고 global을 통해 연동).
 */
public interface SignalPracticeTracker {

	/**
	 * @param practicedPulses ack된 메시지의 스냅샷 pulses — 당사자 기기가 실제로 울린 값이다.
	 *                        신호의 <b>현재</b> pulses와 다르면(리듬이 그사이 바뀌었으면) 세우지 않는다 —
	 *                        늦게 온 ack가 바뀐 리듬을 연습한 것처럼 보이게 하지 않기 위해서다(#60 리뷰).
	 */
	void markPracticed(UUID signalId, List<Integer> practicedPulses, LocalDateTime at);
}
