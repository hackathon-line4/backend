package com.sonulim.domain.signal.domain;

import java.util.List;

import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

/**
 * 공통 규칙: 박자 종류만 저장한다. 초 단위 값(짧게 200 · 중간 500 · 길게 900ms)은 서버 설정값이고
 * 기기 테스트 후 바뀔 수 있다 — 지금은 문서에 적힌 값을 그대로 쓴다.
 */
public enum RhythmBeat {
	SHORT(200),
	MEDIUM(500),
	LONG(900);

	private static final int MIN_BEATS = 2;
	private static final int MAX_BEATS = 5;
	private static final long GAP_MS = 300;

	private final long durationMs;

	RhythmBeat(long durationMs) {
		this.durationMs = durationMs;
	}

	public long getDurationMs() {
		return durationMs;
	}

	/**
	 * 기능명세서: 박자 2~5개. 개수나 토큰이 올바르지 않으면 RHYTHM_INVALID.
	 */
	public static List<RhythmBeat> parseAll(List<String> tokens) {
		if (tokens == null || tokens.size() < MIN_BEATS || tokens.size() > MAX_BEATS) {
			throw new SonulimException(ErrorCode.RHYTHM_INVALID, "박자는 2~5개여야 해요");
		}
		try {
			return tokens.stream().map(RhythmBeat::valueOf).toList();
		} catch (IllegalArgumentException e) {
			throw new SonulimException(ErrorCode.RHYTHM_INVALID, "박자 값이 올바르지 않아요");
		}
	}

	/**
	 * 공통 데이터 형식 문서 기준: 박자 길이 합 + 박자 사이 간격(300ms) * (박자 수 - 1).
	 * 마지막 박자 뒤에는 간격을 더하지 않는다 — 예시(SHORT,SHORT,LONG -> 1900ms)로 검증됨.
	 */
	public static long totalDurationMs(List<RhythmBeat> rhythm) {
		long beatsTotal = rhythm.stream().mapToLong(RhythmBeat::getDurationMs).sum();
		long gaps = (long) (rhythm.size() - 1) * GAP_MS;
		return beatsTotal + gaps;
	}
}
