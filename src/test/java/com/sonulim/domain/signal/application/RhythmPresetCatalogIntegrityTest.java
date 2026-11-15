package com.sonulim.domain.signal.application;

import java.util.List;

import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.RhythmPreset;
import com.sonulim.domain.signal.domain.RhythmPresetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * rhythm_presets 실제 데이터에 같은 진동(pulses)의 프리셋이 두 개 있으면 안 된다 —
 * usedBy(GET /rhythm-presets)·PRESET_IN_USE가 pulses로 "이미 쓰는 리듬"을 판정하므로,
 * 프리셋 자체가 겹치면 그중 하나는 절대 고를 수 없다. RhythmPresetCatalogSeeder를 직접
 * 호출해 실제로 서비스가 채우는 카탈로그를 검증한다(이미 채워져 있으면 그대로 둔다).
 */
@DataJpaTest
class RhythmPresetCatalogIntegrityTest {

	@Autowired
	private RhythmPresetRepository rhythmPresetRepository;

	@Test
	void realCatalogHasNoTwoPresetsWithTheSamePulses() throws Exception {
		new RhythmPresetCatalogSeeder(rhythmPresetRepository).run(null);

		List<List<Integer>> pulses = rhythmPresetRepository.findAll().stream()
				.map(preset -> RhythmBeat.toPulses(preset.getRhythm()))
				.toList();

		assertThat(pulses).hasSize(8);
		assertThat(pulses).doesNotHaveDuplicates();
	}
}
