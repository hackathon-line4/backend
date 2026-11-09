package com.sonulim.domain.signal.domain;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RhythmPresetTest {

	@Test
	void holdsTheRhythmItWasCreatedWith() {
		RhythmPreset preset = new RhythmPreset(List.of(RhythmBeat.SHORT, RhythmBeat.LONG));

		assertThat(preset.getRhythm()).containsExactly(RhythmBeat.SHORT, RhythmBeat.LONG);
		// id는 Hibernate가 저장 시점에 생성한다(SignalTemplate과 같은 관례) — 저장 전엔 null이 맞다.
		assertThat(preset.getId()).isNull();
	}
}
