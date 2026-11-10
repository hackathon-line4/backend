package com.sonulim.domain.signal.domain;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PulseListConverterTest {

	private final PulseListConverter converter = new PulseListConverter();

	@Test
	void preservesOrderRoundTrip() {
		List<Integer> pulses = List.of(150, 150, 150, 500, 700);

		String column = converter.convertToDatabaseColumn(pulses);
		List<Integer> restored = converter.convertToEntityAttribute(column);

		assertThat(column).isEqualTo("150,150,150,500,700");
		assertThat(restored).containsExactly(150, 150, 150, 500, 700);
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
	void nonNumericTokenInColumnFailsFastInsteadOfSilentlyDroppingData() {
		assertThatThrownBy(() -> converter.convertToEntityAttribute("150,abc,700"))
				.isInstanceOf(NumberFormatException.class);
	}
}
