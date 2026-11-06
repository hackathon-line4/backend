package com.sonulim.domain.signal.domain;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RhythmListConverterTest {

	private final RhythmListConverter converter = new RhythmListConverter();

	@Test
	void preservesOrderAndRepeatedBeatsRoundTrip() {
		List<RhythmBeat> rhythm = List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG);

		String column = converter.convertToDatabaseColumn(rhythm);
		List<RhythmBeat> restored = converter.convertToEntityAttribute(column);

		assertThat(column).isEqualTo("SHORT,SHORT,LONG");
		assertThat(restored).containsExactly(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG);
	}

	@Test
	void nullListConvertsToNullColumn() {
		assertThat(converter.convertToDatabaseColumn(null)).isNull();
	}

	@Test
	void emptyListConvertsToNullColumn() {
		assertThat(converter.convertToDatabaseColumn(List.of())).isNull();
	}

	@Test
	void nullColumnConvertsToEmptyList() {
		assertThat(converter.convertToEntityAttribute(null)).isEmpty();
	}

	@Test
	void blankColumnConvertsToEmptyList() {
		assertThat(converter.convertToEntityAttribute("  ")).isEmpty();
	}

	@Test
	void unknownTokenInColumnFailsFastInsteadOfSilentlyDroppingData() {
		assertThatThrownBy(() -> converter.convertToEntityAttribute("SHORT,WRONG,LONG"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
