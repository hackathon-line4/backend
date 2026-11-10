package com.sonulim.domain.signal.application;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.domain.FavoriteSignal;
import com.sonulim.domain.signal.domain.FavoriteSignalRepository;
import com.sonulim.domain.signal.domain.Signal;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FavoriteSignalCommandServiceTest {

	@Mock
	private FavoriteSignalRepository favoriteSignalRepository;

	@Mock
	private SignalRepository signalRepository;

	@Mock
	private LinkAccessGuard linkAccessGuard;

	private final UUID linkId = UUID.randomUUID();
	private final UUID requesterId = UUID.randomUUID();

	private FavoriteSignalCommandService service() {
		return new FavoriteSignalCommandService(favoriteSignalRepository, signalRepository, linkAccessGuard);
	}

	@Test
	void replacesFavoritesInTheGivenOrderAfterClearingExisting() {
		// Signal.createCustom()은 영속화 전엔 id가 null이라(Hibernate가 저장 시점에 채움),
		// "실제 저장된 신호들을 조회했다"는 상황을 흉내 내려고 id가 채워진 mock을 쓴다.
		Signal mockFirst = org.mockito.Mockito.mock(Signal.class);
		Signal mockSecond = org.mockito.Mockito.mock(Signal.class);
		UUID firstId = UUID.randomUUID();
		UUID secondId = UUID.randomUUID();
		given(mockFirst.getId()).willReturn(firstId);
		given(mockSecond.getId()).willReturn(secondId);
		given(signalRepository.findByLinkIdAndDeletedAtIsNull(linkId)).willReturn(List.of(mockFirst, mockSecond));

		List<Signal> result = service().replace(linkId, requesterId, "GUARDIAN", List.of(secondId, firstId));

		assertThat(result).containsExactly(mockSecond, mockFirst);
		verify(linkAccessGuard).verifyAccessible(linkId, requesterId);
		verify(favoriteSignalRepository).deleteByLinkId(linkId);
		// flush()가 delete와 save 사이에 있어야 한다 — 없으면 Hibernate가 같은 트랜잭션 안에서
		// insert를 delete보다 먼저 flush해서 재지정 시 유니크 제약과 충돌한다(실 DB 테스트에서
		// 발견된 버그, FavoriteSignalControllerTest.replacingTwiceFullyOverwritesThePreviousSet).
		var inOrder = inOrder(favoriteSignalRepository);
		inOrder.verify(favoriteSignalRepository).deleteByLinkId(linkId);
		inOrder.verify(favoriteSignalRepository).flush();
		inOrder.verify(favoriteSignalRepository).save(argThatPositionIs(secondId, 0));
		inOrder.verify(favoriteSignalRepository).save(argThatPositionIs(firstId, 1));
	}

	private static FavoriteSignal argThatPositionIs(UUID signalId, int position) {
		return org.mockito.ArgumentMatchers.argThat(
				favorite -> favorite.getSignalId().equals(signalId) && favorite.getPosition() == position);
	}

	@Test
	void rejectsARequesterWhoIsNotAGuardianBeforeTouchingAnything() {
		assertThatThrownBy(() -> service().replace(linkId, requesterId, "RECIPIENT", List.of()))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.ROLE_MISMATCH);
		verifyNoInteractions(linkAccessGuard, signalRepository, favoriteSignalRepository);
	}

	@Test
	void emptyListClearsAllFavoritesAndSavesNothing() {
		List<Signal> result = service().replace(linkId, requesterId, "GUARDIAN", List.of());

		assertThat(result).isEmpty();
		verify(favoriteSignalRepository).deleteByLinkId(linkId);
		verify(favoriteSignalRepository, never()).save(any());
	}

	@Test
	void rejectsMoreThanSixSignalIds() {
		List<UUID> sevenIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

		assertThatThrownBy(() -> service().replace(linkId, requesterId, "GUARDIAN", sevenIds))
				.isInstanceOf(SonulimException.class)
				.satisfies(e -> {
					SonulimException exception = (SonulimException) e;
					assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FAVORITE_LIMIT_EXCEEDED);
					assertThat(exception.getField()).isEqualTo("signalIds");
				});
		verifyNoInteractions(favoriteSignalRepository);
	}

	@Test
	void rejectsDuplicateSignalIds() {
		UUID id = UUID.randomUUID();

		assertThatThrownBy(() -> service().replace(linkId, requesterId, "GUARDIAN", List.of(id, id)))
				.isInstanceOf(SonulimException.class)
				.satisfies(e -> {
					SonulimException exception = (SonulimException) e;
					assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
					assertThat(exception.getField()).isEqualTo("signalIds");
				});
		verifyNoInteractions(favoriteSignalRepository);
	}

	@Test
	void rejectsASignalIdThatDoesNotBelongToThisLinkOrIsDeleted() {
		given(signalRepository.findByLinkIdAndDeletedAtIsNull(linkId)).willReturn(List.of());

		assertThatThrownBy(() -> service().replace(linkId, requesterId, "GUARDIAN", List.of(UUID.randomUUID())))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
		verifyNoInteractions(favoriteSignalRepository);
	}
}
