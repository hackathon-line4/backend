package com.sonulim.domain.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class LinkCodeGeneratorTest {

	private final LinkCodeGenerator generator = new LinkCodeGenerator();

	@Test
	void generatesSixDigitNumericCodes() {
		for (int i = 0; i < 1_000; i++) {
			assertThat(generator.next()).matches("\\d{6}");
		}
	}

	@Test
	void generatesVariedCodes() {
		Set<String> codes = new HashSet<>();
		for (int i = 0; i < 100; i++) {
			codes.add(generator.next());
		}

		assertThat(codes).hasSizeGreaterThan(90);
	}
}
