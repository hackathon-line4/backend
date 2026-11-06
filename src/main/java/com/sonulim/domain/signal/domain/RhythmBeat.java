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
}
