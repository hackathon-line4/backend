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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SignalQueryServiceTest {

	@Mock
	private SignalRepository signalRepository;

	@Mock
	private LinkAccessGuard linkAccessGuard;

	private SignalQueryService service;

	private final UUID linkId = UUID.randomUUID();
	private final UUID requesterId = UUID.randomUUID();

	private SignalQueryService service() {
		return new SignalQueryService(signalRepository, linkAccessGuard);
	}

	@Test
	void listVerifiesLinkAccessThenReturnsThatLinksSignals() {
		service = service();
		Signal signal = Signal.createCustom(linkId, "신호", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
		given(signalRepository.findByLinkIdAndDeletedAtIsNull(linkId)).willReturn(List.of(signal));

		List<Signal> result = service.list(linkId, requesterId);

		verify(linkAccessGuard).verifyAccessible(linkId, requesterId);
		assertThat(result).containsExactly(signal);
	}

	@Test
	void listStillVerifiesAccessEvenWhenThereAreNoSignals() {
		service = service();
		given(signalRepository.findByLinkIdAndDeletedAtIsNull(linkId)).willReturn(List.of());

		List<Signal> result = service.list(linkId, requesterId);

		verify(linkAccessGuard).verifyAccessible(linkId, requesterId);
		assertThat(result).isEmpty();
	}

	@Test
	void listPropagatesGuardRejectionWithoutQueryingSignals() {
		service = service();
		doThrowOnGuard();

		assertThatThrownBy(() -> service.list(linkId, requesterId))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
		verify(signalRepository, never()).findByLinkIdAndDeletedAtIsNull(any());
	}

	@Test
	void detailLooksUpByIdFirstThenVerifiesAccessOnTheSignalsRealLinkId() {
		service = service();
		Signal signal = Signal.createCustom(linkId, "신호", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
		UUID signalId = UUID.randomUUID();
		given(signalRepository.findByIdAndDeletedAtIsNull(signalId)).willReturn(Optional.of(signal));

		Signal result = service.getDetail(linkId, signalId, requesterId);

		verify(linkAccessGuard).verifyAccessible(linkId, requesterId);
		assertThat(result).isEqualTo(signal);
	}

	@Test
	void detailReturnsNotFoundWhenSignalDoesNotExist() {
		service = service();
		UUID signalId = UUID.randomUUID();
		given(signalRepository.findByIdAndDeletedAtIsNull(signalId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.getDetail(linkId, signalId, requesterId))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
		verify(linkAccessGuard, never()).verifyAccessible(any(), any());
	}

	@Test
	void detailReturnsNotFoundWhenRequesterHasNoAccessToTheSignalsRealLink() {
		service = service();
		Signal signal = Signal.createCustom(linkId, "신호", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
		UUID signalId = UUID.randomUUID();
		given(signalRepository.findByIdAndDeletedAtIsNull(signalId)).willReturn(Optional.of(signal));
		doThrowOnGuard();

		assertThatThrownBy(() -> service.getDetail(linkId, signalId, requesterId))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void detailReturnsNotFoundWhenPathLinkIdDoesNotMatchTheSignalsRealLinkEvenWithAccess() {
		service = service();
		UUID actualLinkId = UUID.randomUUID();
		UUID differentPathLinkId = UUID.randomUUID();
		Signal signal = Signal.createCustom(actualLinkId, "신호", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
		UUID signalId = UUID.randomUUID();
		given(signalRepository.findByIdAndDeletedAtIsNull(signalId)).willReturn(Optional.of(signal));

		assertThatThrownBy(() -> service.getDetail(differentPathLinkId, signalId, requesterId))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
		verify(linkAccessGuard).verifyAccessible(eq(actualLinkId), eq(requesterId));
	}

	private void doThrowOnGuard() {
		org.mockito.Mockito.doThrow(new SonulimException(ErrorCode.NOT_FOUND, "기록을 찾을 수 없어요"))
				.when(linkAccessGuard).verifyAccessible(any(), any());
	}
}
