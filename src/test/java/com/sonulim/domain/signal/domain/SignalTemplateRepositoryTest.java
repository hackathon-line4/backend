package com.sonulim.domain.signal.domain;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SignalTemplateRepositoryTest {

	@Autowired
	private SignalTemplateRepository repository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void roundTripsRhythmAndFields() {
		SignalTemplate template = new SignalTemplate("가자", "함께 이동하자는 뜻", SignalCategory.OUTING, "walk",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG));

		SignalTemplate saved = entityManager.persistFlushFind(template);
		entityManager.clear();
		SignalTemplate reloaded = repository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getName()).isEqualTo("가자");
		assertThat(reloaded.getCategory()).isEqualTo(SignalCategory.OUTING);
		assertThat(reloaded.getRhythm()).containsExactly(RhythmBeat.LONG, RhythmBeat.LONG);
	}
}
