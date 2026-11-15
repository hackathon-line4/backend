package com.sonulim.domain.signal.application;

import java.util.List;

import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.RhythmPreset;
import com.sonulim.domain.signal.domain.RhythmPresetRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * rhythm_presets 카탈로그의 MVP 프리셋 8종을 앱 시작 시 한 번만 채운다(이미 있으면
 * 아무 것도 하지 않음). 값은 기획 확인(다연, 이슈 #57·#111) 그대로다.
 */
@Component
public class RhythmPresetCatalogSeeder implements ApplicationRunner {

	private final RhythmPresetRepository rhythmPresetRepository;

	public RhythmPresetCatalogSeeder(RhythmPresetRepository rhythmPresetRepository) {
		this.rhythmPresetRepository = rhythmPresetRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (rhythmPresetRepository.count() > 0) {
			return;
		}
		rhythmPresetRepository.saveAll(List.of(
				new RhythmPreset(List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.SHORT)),
				new RhythmPreset(List.of(RhythmBeat.LONG, RhythmBeat.SHORT, RhythmBeat.SHORT)),
				new RhythmPreset(List.of(RhythmBeat.SHORT, RhythmBeat.LONG, RhythmBeat.SHORT)),
				new RhythmPreset(List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG)),
				new RhythmPreset(List.of(RhythmBeat.LONG, RhythmBeat.LONG, RhythmBeat.SHORT)),
				new RhythmPreset(List.of(RhythmBeat.SHORT, RhythmBeat.LONG, RhythmBeat.LONG)),
				new RhythmPreset(List.of(RhythmBeat.LONG, RhythmBeat.SHORT, RhythmBeat.LONG)),
				new RhythmPreset(List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG, RhythmBeat.LONG))));
	}
}
