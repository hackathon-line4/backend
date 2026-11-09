package com.sonulim.domain.signal.domain;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class FavoriteSignalRepositoryTest {

	@Autowired
	private FavoriteSignalRepository repository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void roundTripsFields() {
		UUID linkId = UUID.randomUUID();
		UUID signalId = UUID.randomUUID();
		FavoriteSignal favorite = new FavoriteSignal(linkId, signalId, 2);

		FavoriteSignal saved = entityManager.persistFlushFind(favorite);
		entityManager.clear();
		FavoriteSignal reloaded = repository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getLinkId()).isEqualTo(linkId);
		assertThat(reloaded.getSignalId()).isEqualTo(signalId);
		assertThat(reloaded.getPosition()).isEqualTo(2);
	}

	@Test
	void findsByLinkIdOrderedByPosition() {
		UUID linkId = UUID.randomUUID();
		UUID signalA = UUID.randomUUID();
		UUID signalB = UUID.randomUUID();
		UUID signalC = UUID.randomUUID();
		// 일부러 순서를 뒤섞어 저장해서 정렬이 실제로 적용되는지 확인한다.
		entityManager.persistAndFlush(new FavoriteSignal(linkId, signalB, 1));
		entityManager.persistAndFlush(new FavoriteSignal(linkId, signalC, 2));
		entityManager.persistAndFlush(new FavoriteSignal(linkId, signalA, 0));

		List<FavoriteSignal> result = repository.findByLinkIdOrderByPosition(linkId);

		assertThat(result).extracting(FavoriteSignal::getSignalId).containsExactly(signalA, signalB, signalC);
	}

	@Test
	void findsNothingForADifferentLink() {
		entityManager.persistAndFlush(new FavoriteSignal(UUID.randomUUID(), UUID.randomUUID(), 0));

		List<FavoriteSignal> result = repository.findByLinkIdOrderByPosition(UUID.randomUUID());

		assertThat(result).isEmpty();
	}

	@Test
	void deleteByLinkIdRemovesOnlyThatLinksFavorites() {
		UUID linkId = UUID.randomUUID();
		UUID otherLinkId = UUID.randomUUID();
		entityManager.persistAndFlush(new FavoriteSignal(linkId, UUID.randomUUID(), 0));
		entityManager.persistAndFlush(new FavoriteSignal(otherLinkId, UUID.randomUUID(), 0));

		repository.deleteByLinkId(linkId);
		entityManager.flush();

		assertThat(repository.findByLinkIdOrderByPosition(linkId)).isEmpty();
		assertThat(repository.findByLinkIdOrderByPosition(otherLinkId)).hasSize(1);
	}
}
