package com.sonulim.domain.signal.domain;

import java.util.List;
import java.util.UUID;

import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignalMutationTest {

	private final UUID linkId = UUID.randomUUID();

	@Test
	void customSignalAllowsNameAndRhythmChanges() {
		Signal signal = Signal.createCustom(linkId, "이름", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG));

		signal.updateName("새 이름");
		signal.updateRhythm(List.of(RhythmBeat.LONG, RhythmBeat.LONG, RhythmBeat.SHORT));
		signal.updateDescription("새 설명");
		signal.updateCategory(SignalCategory.EMERGENCY);
		signal.updateIcon("new-icon");

		assertThat(signal.getName()).isEqualTo("새 이름");
		assertThat(signal.getRhythm()).containsExactly(RhythmBeat.LONG, RhythmBeat.LONG, RhythmBeat.SHORT);
		assertThat(signal.getDescription()).isEqualTo("새 설명");
		assertThat(signal.getCategory()).isEqualTo(SignalCategory.EMERGENCY);
		assertThat(signal.getIcon()).isEqualTo("new-icon");
	}

	@Test
	void templateSignalRejectsNameChange() {
		Signal signal = Signal.fromTemplate(linkId, UUID.randomUUID(), "기본 신호", null,
				SignalCategory.CHECK, "icon", List.of(RhythmBeat.SHORT, RhythmBeat.LONG),
				RhythmBeat.toPulses(List.of(RhythmBeat.SHORT, RhythmBeat.LONG)));

		assertThatThrownBy(() -> signal.updateName("바꾼 이름"))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.SIGNAL_NOT_EDITABLE);
	}

	@Test
	void templateSignalRejectsRhythmChange() {
		Signal signal = Signal.fromTemplate(linkId, UUID.randomUUID(), "기본 신호", null,
				SignalCategory.CHECK, "icon", List.of(RhythmBeat.SHORT, RhythmBeat.LONG),
				RhythmBeat.toPulses(List.of(RhythmBeat.SHORT, RhythmBeat.LONG)));

		assertThatThrownBy(() -> signal.updateRhythm(List.of(RhythmBeat.LONG, RhythmBeat.LONG)))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.SIGNAL_NOT_EDITABLE);
	}

	@Test
	void stampingAnIdempotencyKeyMakesItReadableAgain() {
		Signal signal = Signal.createCustom(linkId, "이름", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
		UUID idempotencyKey = UUID.randomUUID();

		signal.stampIdempotencyKey(idempotencyKey);

		assertThat(signal.getIdempotencyKey()).isEqualTo(idempotencyKey);
	}

	@Test
	void templateSignalStillAllowsDescriptionCategoryAndIconChanges() {
		Signal signal = Signal.fromTemplate(linkId, UUID.randomUUID(), "기본 신호", null,
				SignalCategory.CHECK, "icon", List.of(RhythmBeat.SHORT, RhythmBeat.LONG),
				RhythmBeat.toPulses(List.of(RhythmBeat.SHORT, RhythmBeat.LONG)));

		signal.updateDescription("새 설명");
		signal.updateCategory(SignalCategory.EMERGENCY);
		signal.updateIcon("new-icon");

		assertThat(signal.getDescription()).isEqualTo("새 설명");
		assertThat(signal.getCategory()).isEqualTo(SignalCategory.EMERGENCY);
		assertThat(signal.getIcon()).isEqualTo("new-icon");
	}
}
