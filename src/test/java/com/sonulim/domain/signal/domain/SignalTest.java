package com.sonulim.domain.signal.domain;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignalTest {

	private static final UUID LINK_ID = UUID.randomUUID();

	@Test
	void calculatesDurationAsBeatsPlusGapsBetweenThemMatchingTheCommonDataFormatExample() {
		// 촉신호 -> 진동 변환 규칙 문서: rhythm ["SHORT","SHORT","LONG"] -> durationMs 1600
		// 150(SHORT) + 150(간격) + 150(SHORT) + 150(간격) + 1000(LONG) = 1600, 마지막 뒤 간격 없음
		Signal signal = Signal.createCustom(LINK_ID, "약 먹을 시간", "저녁 약을 먹자는 뜻",
				SignalCategory.DAILY, "pill", List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG));

		assertThat(signal.calculateDurationMs()).isEqualTo(1600);
	}

	@Test
	void twoBeatRhythmHasExactlyOneGap() {
		Signal signal = Signal.createCustom(LINK_ID, "두 박자", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.SHORT));

		assertThat(signal.calculateDurationMs()).isEqualTo(150 + 150 + 150);
	}

	@Test
	void customSignalPulsesAreComputedFromRhythm() {
		Signal signal = Signal.createCustom(LINK_ID, "약 먹을 시간", null, SignalCategory.DAILY, "pill",
				List.of(RhythmBeat.LONG, RhythmBeat.SHORT, RhythmBeat.MEDIUM));

		assertThat(signal.getPulses()).containsExactly(1000, 150, 150, 150, 700);
	}

	@Test
	void updatingRhythmRecomputesPulses() {
		Signal signal = Signal.createCustom(LINK_ID, "이름", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.SHORT));

		signal.updateRhythm(List.of(RhythmBeat.LONG, RhythmBeat.LONG));

		assertThat(signal.getPulses()).containsExactly(1000, 150, 1000);
	}

	@Test
	void templateSourcedSignalCopiesRhythmAndPulsesFromTheTemplate() {
		Signal signal = Signal.fromTemplate(LINK_ID, UUID.randomUUID(), "가자", "함께 이동하자는 뜻",
				SignalCategory.OUTING, "walk", null, List.of(150, 150, 150, 150, 150, 150, 150));

		assertThat(signal.getRhythm()).isNull();
		assertThat(signal.getPulses()).containsExactly(150, 150, 150, 150, 150, 150, 150);
		assertThat(signal.calculateDurationMs()).isEqualTo(1050);
	}

	@Test
	void newCustomSignalStartsUnpracticedAndNotDeleted() {
		Signal signal = Signal.createCustom(LINK_ID, "신규", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG));

		assertThat(signal.isPracticed()).isFalse();
		assertThat(signal.getDeletedAt()).isNull();
		assertThat(signal.getSource()).isEqualTo(SignalSource.CUSTOM);
		// id는 Hibernate가 저장 시점에 생성한다(account.User와 같은 관례) — 저장 전엔 null이 맞다.
		assertThat(signal.getId()).isNull();
	}

	@Test
	void softDeleteSetsDeletedAt() {
		Signal signal = Signal.createCustom(LINK_ID, "삭제 예정", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG));

		signal.softDelete();

		assertThat(signal.getDeletedAt()).isNotNull();
	}
}
