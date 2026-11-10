package com.sonulim.domain.signal.application;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.domain.RhythmPresetRepository;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.global.linkaccess.LinkAccessGuard;
import org.springframework.stereotype.Service;

/**
 * M-3 "리듬 프리셋 목록" — GET /api/v1/rhythm-presets. usedBy는 POST/PATCH의
 * PRESET_IN_USE와 반드시 같은 기준(SignalRepository.findByLinkIdAndRhythm)을 써야
 * 한다 — 목록에서 "사용 가능"으로 보였는데 생성 시점엔 막히는 불일치가 없도록.
 */
@Service
public class RhythmPresetQueryService {

	private final RhythmPresetRepository rhythmPresetRepository;
	private final SignalRepository signalRepository;
	private final LinkAccessGuard linkAccessGuard;

	public RhythmPresetQueryService(RhythmPresetRepository rhythmPresetRepository, SignalRepository signalRepository,
			LinkAccessGuard linkAccessGuard) {
		this.rhythmPresetRepository = rhythmPresetRepository;
		this.signalRepository = signalRepository;
		this.linkAccessGuard = linkAccessGuard;
	}

	public List<RhythmPresetView> list(UUID requesterId, String role, UUID linkId) {
		GuardianOnly.require(role);
		linkAccessGuard.verifyAccessible(linkId, requesterId);

		return rhythmPresetRepository.findAll().stream()
				.map(preset -> new RhythmPresetView(preset,
						signalRepository.findByLinkIdAndRhythm(linkId, preset.getRhythm(), null).orElse(null)))
				.toList();
	}
}
