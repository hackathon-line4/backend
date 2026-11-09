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
import static org.mockito.Mockito.verifyNoInteractions;

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
	void verifiesAccessThenReportsValidWithDurationWhenNothingMatches() {
		RhythmCheckResult result = service().checkRhythm(linkId, requesterId, "GUARDIAN", List.of("SHORT", "LONG"),
				null);

		verify(linkAccessGuard).verifyAccessible(linkId, requesterId);
		assertThat(result.valid()).isTrue();
		assertThat(result.durationMs()).isEqualTo(1300);
		assertThat(result.conflictSignal()).isNull();
	}

	@Test
	void reportsInvalidWithTheConflictingSignal() {
		Signal existing = Signal.createCustom(linkId, "가자", null, SignalCategory.OUTING, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
		given(signalRepository.findByLinkIdAndPulses(linkId, RhythmBeat.toPulses(List.of(RhythmBeat.SHORT, RhythmBeat.LONG)), null))
				.willReturn(Optional.of(existing));

		RhythmCheckResult result = service().checkRhythm(linkId, requesterId, "GUARDIAN", List.of("SHORT", "LONG"),
				null);

		assertThat(result.valid()).isFalse();
		assertThat(result.durationMs()).isEqualTo(1300);
		assertThat(result.conflictSignal().name()).isEqualTo("가자");
	}

	@Test
	void passesExcludeSignalIdThroughToTheRepositoryLookup() {
		UUID excludeSignalId = UUID.randomUUID();

		service().checkRhythm(linkId, requesterId, "GUARDIAN", List.of("SHORT", "LONG"), excludeSignalId);

		verify(signalRepository).findByLinkIdAndPulses(linkId, RhythmBeat.toPulses(List.of(RhythmBeat.SHORT, RhythmBeat.LONG)),
				excludeSignalId);
	}

	@Test
	void rejectsAnInvalidRhythmBeforeCheckingDuplicates() {
		assertThatThrownBy(() -> service().checkRhythm(linkId, requesterId, "GUARDIAN", List.of("SHORT"), null))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.RHYTHM_INVALID);
	}

	@Test
	void rejectsARequesterWhoIsNotAGuardianBeforeTouchingTheGuardOrRepository() {
		assertThatThrownBy(
				() -> service().checkRhythm(linkId, requesterId, "RECIPIENT", List.of("SHORT", "LONG"), null))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.ROLE_MISMATCH);
		verifyNoInteractions(linkAccessGuard, signalRepository);
	}
}
