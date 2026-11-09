package com.sonulim.domain.signal.domain;

import java.util.List;

import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RhythmBeatTest {

	@Test
	void parsesTwoToFiveValidTokensInOrder() {
		List<RhythmBeat> rhythm = RhythmBeat.parseAll(List.of("SHORT", "SHORT", "LONG"));

		assertThat(rhythm).containsExactly(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG);
	}

	@Test
	void rejectsFewerThanTwoBeats() {
		assertThatThrownBy(() -> RhythmBeat.parseAll(List.of("SHORT")))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.RHYTHM_INVALID);
	}

	@Test
	void rejectsMoreThanFiveBeats() {
		assertThatThrownBy(() -> RhythmBeat.parseAll(
				List.of("SHORT", "SHORT", "SHORT", "SHORT", "SHORT", "SHORT")))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.RHYTHM_INVALID);
	}

	@Test
	void rejectsNullRhythm() {
		assertThatThrownBy(() -> RhythmBeat.parseAll(null))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.RHYTHM_INVALID);
	}

	@Test
	void rejectsAnUnknownBeatToken() {
		assertThatThrownBy(() -> RhythmBeat.parseAll(List.of("SHORT", "WRONG")))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.RHYTHM_INVALID);
	}

	@Test
	void reportsTheRhythmFieldWhenTheCountIsInvalid() {
		assertThatThrownBy(() -> RhythmBeat.parseAll(List.of("SHORT")))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getField())
				.isEqualTo("rhythm");
	}

	@Test
	void reportsTheRhythmFieldWhenATokenIsUnknown() {
		assertThatThrownBy(() -> RhythmBeat.parseAll(List.of("SHORT", "WRONG")))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getField())
				.isEqualTo("rhythm");
	}

	@Test
	void sumsBeatDurationsPlusGapsBetweenBeatsButNotAfterTheLastOne() {
		long durationMs = RhythmBeat.totalDurationMs(List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG));

		assertThat(durationMs).isEqualTo(1600);
	}

	@Test
	void convertsRhythmToAPulsesArrayInterleavingBeatsAndGaps() {
		List<Integer> pulses = RhythmBeat.toPulses(List.of(RhythmBeat.LONG, RhythmBeat.SHORT, RhythmBeat.MEDIUM));

		assertThat(pulses).containsExactly(1000, 150, 150, 150, 700);
	}

	@Test
	void pulsesSumMatchesTotalDurationMs() {
		List<RhythmBeat> rhythm = List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG);

		int sum = RhythmBeat.toPulses(rhythm).stream().mapToInt(Integer::intValue).sum();

		assertThat(sum).isEqualTo(RhythmBeat.totalDurationMs(rhythm));
	}
}
