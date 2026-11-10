package com.sonulim.domain.signal.domain;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignalTest {

	private static final UUID LINK_ID = UUID.randomUUID();

	@Test
	void calculatesDurationAsBeatsPlusGapsBetweenThemMatchingTheCommonDataFormatExample() {
		// 공통 데이터 형식 문서 예시: rhythm ["SHORT","SHORT","LONG"] -> durationMs 1900
		// 200(SHORT) + 300(간격) + 200(SHORT) + 300(간격) + 900(LONG) = 1900, 마지막 뒤 간격 없음
		Signal signal = Signal.createCustom(LINK_ID, "약 먹을 시간", "저녁 약을 먹자는 뜻",
				SignalCategory.DAILY, "pill", List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG));

		assertThat(signal.calculateDurationMs()).isEqualTo(1900);
	}

	@Test
	void twoBeatRhythmHasExactlyOneGap() {
		Signal signal = Signal.createCustom(LINK_ID, "두 박자", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.SHORT));

		assertThat(signal.calculateDurationMs()).isEqualTo(200 + 300 + 200);
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
