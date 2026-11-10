package com.sonulim.domain.signal.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.RhythmPreset;
import com.sonulim.domain.signal.domain.RhythmPresetRepository;
import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.linkaccess.LinkAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RhythmPresetQueryServiceTest {

	@Mock
	private RhythmPresetRepository rhythmPresetRepository;

	@Mock
	private SignalRepository signalRepository;

	@Mock
	private LinkAccessGuard linkAccessGuard;

	private final UUID requesterId = UUID.randomUUID();
	private final UUID linkId = UUID.randomUUID();

	private RhythmPresetQueryService service() {
		return new RhythmPresetQueryService(rhythmPresetRepository, signalRepository, linkAccessGuard);
	}

	@Test
	void rejectsARequesterWhoIsNotAGuardian() {
		assertThatThrownBy(() -> service().list(requesterId, "RECIPIENT", linkId))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.ROLE_MISMATCH);
		verifyNoInteractions(rhythmPresetRepository, linkAccessGuard);
	}

	@Test
	void verifiesLinkAccessBeforeComputingResults() {
		given(rhythmPresetRepository.findAll()).willReturn(List.of());

		service().list(requesterId, "GUARDIAN", linkId);

		verify(linkAccessGuard).verifyAccessible(linkId, requesterId);
	}

	@Test
	void reportsUsedByNullWhenNoSignalUsesThatRhythm() {
		RhythmPreset preset = new RhythmPreset(List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
		given(rhythmPresetRepository.findAll()).willReturn(List.of(preset));
		given(signalRepository.findByLinkIdAndRhythm(linkId, List.of(RhythmBeat.SHORT, RhythmBeat.LONG), null))
				.willReturn(Optional.empty());

		List<RhythmPresetView> result = service().list(requesterId, "GUARDIAN", linkId);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).preset()).isEqualTo(preset);
		assertThat(result.get(0).usedBy()).isNull();
	}

	@Test
	void reportsUsedByTheSignalThatAlreadyUsesThatRhythm() {
		RhythmPreset preset = new RhythmPreset(List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
		Signal existing = Signal.createCustom(linkId, "가자", null, SignalCategory.OUTING, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
		given(rhythmPresetRepository.findAll()).willReturn(List.of(preset));
		given(signalRepository.findByLinkIdAndRhythm(linkId, List.of(RhythmBeat.SHORT, RhythmBeat.LONG), null))
				.willReturn(Optional.of(existing));

		List<RhythmPresetView> result = service().list(requesterId, "GUARDIAN", linkId);

		assertThat(result.get(0).usedBy()).isEqualTo(existing);
	}

	@Test
	void usesTheSameCriterionAsRhythmDuplicateCheckNoExclusion() {
		// usedBy와 PRESET_IN_USE가 같은 기준을 쓴다는 요구사항 — findByLinkIdAndRhythm을
		// excludeId=null로 호출하는 것 자체가 "본인 제외 없이 전체 조회"라는 그 기준이다.
		RhythmPreset preset = new RhythmPreset(List.of(RhythmBeat.LONG, RhythmBeat.LONG));
		given(rhythmPresetRepository.findAll()).willReturn(List.of(preset));

		service().list(requesterId, "GUARDIAN", linkId);

		verify(signalRepository).findByLinkIdAndRhythm(linkId, List.of(RhythmBeat.LONG, RhythmBeat.LONG), null);
	}
}
