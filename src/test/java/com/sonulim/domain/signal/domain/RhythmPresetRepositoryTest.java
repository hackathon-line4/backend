package com.sonulim.domain.signal.domain;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RhythmPresetRepositoryTest {

	@Autowired
	private RhythmPresetRepository repository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void roundTripsRhythm() {
		RhythmPreset preset = new RhythmPreset(List.of(RhythmBeat.SHORT, RhythmBeat.SHORT));

		RhythmPreset saved = entityManager.persistFlushFind(preset);
		entityManager.clear();
		RhythmPreset reloaded = repository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getRhythm()).containsExactly(RhythmBeat.SHORT, RhythmBeat.SHORT);
	}
}
