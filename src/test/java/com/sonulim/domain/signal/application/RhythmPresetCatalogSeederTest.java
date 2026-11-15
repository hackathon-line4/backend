package com.sonulim.domain.signal.application;

import java.util.List;

import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.RhythmPreset;
import com.sonulim.domain.signal.domain.RhythmPresetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RhythmPresetCatalogSeederTest {

	@Mock
	private RhythmPresetRepository rhythmPresetRepository;

	private RhythmPresetCatalogSeeder seeder() {
		return new RhythmPresetCatalogSeeder(rhythmPresetRepository);
	}

	@Test
	void seedsTheEightMvpPresetsWhenTheCatalogIsEmpty() throws Exception {
		given(rhythmPresetRepository.count()).willReturn(0L);

		seeder().run(null);

		ArgumentCaptor<List<RhythmPreset>> captor = ArgumentCaptor.forClass(List.class);
		verify(rhythmPresetRepository).saveAll(captor.capture());
		List<RhythmPreset> saved = captor.getValue();

		assertThat(saved).extracting(RhythmPreset::getRhythm).containsExactlyInAnyOrder(
				List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.SHORT),
				List.of(RhythmBeat.LONG, RhythmBeat.SHORT, RhythmBeat.SHORT),
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG, RhythmBeat.SHORT),
				List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG),
				List.of(RhythmBeat.LONG, RhythmBeat.LONG, RhythmBeat.SHORT),
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG, RhythmBeat.LONG),
				List.of(RhythmBeat.LONG, RhythmBeat.SHORT, RhythmBeat.LONG),
				List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG, RhythmBeat.LONG));
	}

	@Test
	void doesNothingWhenTheCatalogAlreadyHasPresets() throws Exception {
		given(rhythmPresetRepository.count()).willReturn(1L);

		seeder().run(null);

		verify(rhythmPresetRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
	}
}
