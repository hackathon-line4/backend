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
import static org.mockito.Mockito.verifyNoInteractions;

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

		List<Signal> result = service.list(linkId, requesterId, "GUARDIAN", null);

		verify(linkAccessGuard).verifyAccessible(linkId, requesterId);
		assertThat(result).containsExactly(signal);
	}

	@Test
	void listStillVerifiesAccessEvenWhenThereAreNoSignals() {
		service = service();
		given(signalRepository.findByLinkIdAndDeletedAtIsNull(linkId)).willReturn(List.of());

		List<Signal> result = service.list(linkId, requesterId, "GUARDIAN", null);

		verify(linkAccessGuard).verifyAccessible(linkId, requesterId);
		assertThat(result).isEmpty();
	}

	@Test
	void listPropagatesGuardRejectionWithoutQueryingSignals() {
		service = service();
		doThrowOnGuard();

		assertThatThrownBy(() -> service.list(linkId, requesterId, "GUARDIAN", null))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
		verify(signalRepository, never()).findByLinkIdAndDeletedAtIsNull(any());
	}

	@Test
	void listRejectsARequesterWhoIsNotAGuardianBeforeTouchingTheGuardOrRepository() {
		service = service();

		assertThatThrownBy(() -> service.list(linkId, requesterId, "RECIPIENT", null))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.ROLE_MISMATCH);
		verifyNoInteractions(linkAccessGuard, signalRepository);
	}

	@Test
	void listSortsByCategoryOrderThenByCreationOrderWithinEachCategory() {
		service = service();
		// getCreatedAt()은 카테고리만으로 순서가 갈리는 쌍에서는 호출되지 않을 수 있어
		// lenient로 둔다 — 검증 대상은 최종 정렬 결과다.
		Signal emergency = org.mockito.Mockito.mock(Signal.class);
		given(emergency.getCategory()).willReturn(SignalCategory.EMERGENCY);
		org.mockito.Mockito.lenient().when(emergency.getCreatedAt())
				.thenReturn(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
		Signal dailyOlder = org.mockito.Mockito.mock(Signal.class);
		given(dailyOlder.getCategory()).willReturn(SignalCategory.DAILY);
		org.mockito.Mockito.lenient().when(dailyOlder.getCreatedAt())
				.thenReturn(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
		Signal dailyNewer = org.mockito.Mockito.mock(Signal.class);
		given(dailyNewer.getCategory()).willReturn(SignalCategory.DAILY);
		org.mockito.Mockito.lenient().when(dailyNewer.getCreatedAt())
				.thenReturn(java.time.LocalDateTime.of(2026, 1, 2, 0, 0));
		Signal outing = org.mockito.Mockito.mock(Signal.class);
		given(outing.getCategory()).willReturn(SignalCategory.OUTING);
		org.mockito.Mockito.lenient().when(outing.getCreatedAt())
				.thenReturn(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
		// 일부러 뒤섞인 순서로 돌려줘서 정렬이 실제로 적용되는지 확인한다.
		given(signalRepository.findByLinkIdAndDeletedAtIsNull(linkId))
				.willReturn(List.of(emergency, outing, dailyNewer, dailyOlder));

		List<Signal> result = service.list(linkId, requesterId, "GUARDIAN", null);

		assertThat(result).containsExactly(dailyOlder, dailyNewer, outing, emergency);
	}

	@Test
	void listFiltersByCategoryWhenGiven() {
		service = service();
		Signal daily = Signal.createCustom(linkId, "일상", null, SignalCategory.DAILY, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
		Signal outing = Signal.createCustom(linkId, "외출", null, SignalCategory.OUTING, "icon",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG));
		given(signalRepository.findByLinkIdAndDeletedAtIsNull(linkId)).willReturn(List.of(daily, outing));

		List<Signal> result = service.list(linkId, requesterId, "GUARDIAN", SignalCategory.OUTING);

		assertThat(result).containsExactly(outing);
	}

	@Test
	void detailLooksUpByIdFirstThenVerifiesAccessOnTheSignalsRealLinkId() {
		service = service();
		Signal signal = Signal.createCustom(linkId, "신호", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
		UUID signalId = UUID.randomUUID();
		given(signalRepository.findByIdAndDeletedAtIsNull(signalId)).willReturn(Optional.of(signal));

		Signal result = service.getDetail(linkId, signalId, requesterId, "GUARDIAN");

		verify(linkAccessGuard).verifyAccessible(linkId, requesterId);
		assertThat(result).isEqualTo(signal);
	}

	@Test
	void detailReturnsNotFoundWhenSignalDoesNotExist() {
		service = service();
		UUID signalId = UUID.randomUUID();
		given(signalRepository.findByIdAndDeletedAtIsNull(signalId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.getDetail(linkId, signalId, requesterId, "GUARDIAN"))
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

		assertThatThrownBy(() -> service.getDetail(linkId, signalId, requesterId, "GUARDIAN"))
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

		assertThatThrownBy(() -> service.getDetail(differentPathLinkId, signalId, requesterId, "GUARDIAN"))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
		verify(linkAccessGuard).verifyAccessible(eq(actualLinkId), eq(requesterId));
	}

	@Test
	void detailRejectsARequesterWhoIsNotAGuardianBeforeLookingUpTheSignal() {
		service = service();

		assertThatThrownBy(() -> service.getDetail(linkId, UUID.randomUUID(), requesterId, "RECIPIENT"))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.ROLE_MISMATCH);
		verifyNoInteractions(linkAccessGuard, signalRepository);
	}

	private void doThrowOnGuard() {
		org.mockito.Mockito.doThrow(new SonulimException(ErrorCode.NOT_FOUND, "기록을 찾을 수 없어요"))
				.when(linkAccessGuard).verifyAccessible(any(), any());
	}
}
