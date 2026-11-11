package com.sonulim.domain.signal.application;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.domain.FavoriteSignal;
import com.sonulim.domain.signal.domain.FavoriteSignalRepository;
import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FavoriteSignalQueryServiceTest {

	@Mock
	private FavoriteSignalRepository favoriteSignalRepository;

	@Mock
	private SignalRepository signalRepository;

	private final UUID linkId = UUID.randomUUID();

	private FavoriteSignalQueryService service() {
		return new FavoriteSignalQueryService(favoriteSignalRepository, signalRepository);
	}

	@Test
	void returnsFavoritesInTheirPositionOrder() {
		UUID firstId = UUID.randomUUID();
		UUID secondId = UUID.randomUUID();
		Signal first = signalWithId(firstId);
		Signal second = signalWithId(secondId);
		given(signalRepository.findByLinkIdAndDeletedAtIsNull(linkId)).willReturn(List.of(first, second));
		given(favoriteSignalRepository.findByLinkIdOrderByPosition(linkId)).willReturn(List.of(
				new FavoriteSignal(linkId, secondId, 0),
				new FavoriteSignal(linkId, firstId, 1)));

		assertThat(service().list(linkId)).containsExactly(second, first);
	}

	// 지정한 뒤 신호가 삭제되면 favorite_signals에는 남지만 목록에서는 뺀다.
	@Test
	void skipsFavoritesWhoseSignalWasDeleted() {
		UUID remainingId = UUID.randomUUID();
		Signal remaining = signalWithId(remainingId);
		given(signalRepository.findByLinkIdAndDeletedAtIsNull(linkId)).willReturn(List.of(remaining));
		given(favoriteSignalRepository.findByLinkIdOrderByPosition(linkId)).willReturn(List.of(
				new FavoriteSignal(linkId, UUID.randomUUID(), 0),
				new FavoriteSignal(linkId, remainingId, 1)));

		assertThat(service().list(linkId)).containsExactly(remaining);
	}

	@Test
	void noFavoritesIsAnEmptyList() {
		given(signalRepository.findByLinkIdAndDeletedAtIsNull(linkId)).willReturn(List.of());
		given(favoriteSignalRepository.findByLinkIdOrderByPosition(linkId)).willReturn(List.of());

		assertThat(service().list(linkId)).isEmpty();
	}

	// Signal은 영속화 전 id가 null이라 id가 채워진 mock을 쓴다(FavoriteSignalCommandServiceTest와 같은 이유).
	private static Signal signalWithId(UUID id) {
		Signal signal = org.mockito.Mockito.mock(Signal.class);
		given(signal.getId()).willReturn(id);
		return signal;
	}
}
