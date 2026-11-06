package com.sonulim.domain.signal.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.sonulim.domain.signal.domain.RhythmBeat;
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

@ExtendWith(MockitoExtension.class)
class RhythmCheckTest {

	@Mock
	private SignalRepository signalRepository;

	@Mock
	private LinkAccessGuard linkAccessGuard;

	private final UUID linkId = UUID.randomUUID();
	private final UUID requesterId = UUID.randomUUID();

	private SignalQueryService service() {
		return new SignalQueryService(signalRepository, linkAccessGuard);
	}

	@Test
	void verifiesAccessThenReportsNoDuplicateWhenNothingMatches() {
		RhythmCheckResult result = service().checkRhythm(linkId, requesterId, List.of("SHORT", "LONG"));

		verify(linkAccessGuard).verifyAccessible(linkId, requesterId);
		assertThat(result.duplicate()).isFalse();
		assertThat(result.conflictingSignalName()).isNull();
	}

	@Test
	void reportsDuplicateWithTheConflictingSignalsName() {
		Signal existing = Signal.createCustom(linkId, "가자", null, SignalCategory.OUTING, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
		given(signalRepository.findByLinkIdAndRhythm(linkId, List.of(RhythmBeat.SHORT, RhythmBeat.LONG), null))
				.willReturn(Optional.of(existing));

		RhythmCheckResult result = service().checkRhythm(linkId, requesterId, List.of("SHORT", "LONG"));

		assertThat(result.duplicate()).isTrue();
		assertThat(result.conflictingSignalName()).isEqualTo("가자");
	}

	@Test
	void rejectsAnInvalidRhythmBeforeCheckingDuplicates() {
		assertThatThrownBy(() -> service().checkRhythm(linkId, requesterId, List.of("SHORT")))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.RHYTHM_INVALID);
	}
}
