package com.sonulim.domain.signal.domain;

import java.util.ArrayList;
import java.util.List;

import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

/**
 * 공통 규칙: 박자 종류만 저장한다. 초 단위 값(짧게 150 · 중간 700 · 길게 1000ms, 박자 사이 쉼
 * 150ms)은 촉신호 → 진동 변환 규칙 문서(기획 확인) 기준이다.
 */
public enum RhythmBeat {
	SHORT(150),
	MEDIUM(700),
	LONG(1000);

	private static final int MIN_BEATS = 2;
	private static final int MAX_BEATS = 5;
	private static final long GAP_MS = 150;

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
			throw SonulimException.forField(ErrorCode.RHYTHM_INVALID, "박자는 2개에서 5개까지 넣을 수 있어요", "rhythm");
		}
		try {
			return tokens.stream().map(RhythmBeat::valueOf).toList();
		} catch (IllegalArgumentException e) {
			throw SonulimException.forField(ErrorCode.RHYTHM_INVALID, "박자 값이 올바르지 않아요", "rhythm");
		}
	}

	/**
	 * durationMs는 pulses 배열의 합과 같다(촉신호 → 진동 변환 규칙 문서).
	 */
	public static long totalDurationMs(List<RhythmBeat> rhythm) {
		return toPulses(rhythm).stream().mapToLong(Integer::longValue).sum();
	}

	/**
	 * rhythm을 [울림, 쉼, 울림, …] 순서의 ms 배열로 바꾼다. 첫 값은 울림이고 앞에 0을 붙이지
	 * 않는다 — 기기에서 재생할 때만 프론트가 0을 붙인다(구현 메모, 촉신호 → 진동 변환 규칙 문서).
	 */
	public static List<Integer> toPulses(List<RhythmBeat> rhythm) {
		List<Integer> pulses = new ArrayList<>();
		for (int i = 0; i < rhythm.size(); i++) {
			if (i > 0) {
				pulses.add((int) GAP_MS);
			}
			pulses.add((int) rhythm.get(i).getDurationMs());
		}
		return List.copyOf(pulses);
	}
}
