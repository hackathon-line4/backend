package com.sonulim.domain.signal.domain;

/**
 * 공통 규칙: 박자 종류만 저장한다. 초 단위 값(짧게 200 · 중간 500 · 길게 900ms)은 서버 설정값이고
 * 기기 테스트 후 바뀔 수 있다 — 지금은 문서에 적힌 값을 그대로 쓴다.
 */
public enum RhythmBeat {
	SHORT(200),
	MEDIUM(500),
	LONG(900);

	private final long durationMs;

	RhythmBeat(long durationMs) {
		this.durationMs = durationMs;
	}

	public long getDurationMs() {
		return durationMs;
	}
}
