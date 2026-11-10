package com.sonulim.domain.signal.domain;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignalTemplateTest {

	@Test
	void holdsTheFieldsItWasCreatedWith() {
		SignalTemplate template = new SignalTemplate("가자", "함께 이동하자는 뜻", SignalCategory.OUTING, "walk",
				null, List.of(150, 150, 150, 150, 150, 150, 150));

		assertThat(template.getName()).isEqualTo("가자");
		assertThat(template.getDescription()).isEqualTo("함께 이동하자는 뜻");
		assertThat(template.getCategory()).isEqualTo(SignalCategory.OUTING);
		assertThat(template.getIcon()).isEqualTo("walk");
		assertThat(template.getRhythm()).isNull();
		assertThat(template.getPulses()).containsExactly(150, 150, 150, 150, 150, 150, 150);
		// id는 Hibernate가 저장 시점에 생성한다(Signal과 같은 관례) — 저장 전엔 null이 맞다.
		assertThat(template.getId()).isNull();
	}
}
