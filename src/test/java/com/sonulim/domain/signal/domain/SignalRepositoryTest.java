package com.sonulim.domain.signal.domain;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(SignalRepositoryTest.AuditingTestConfig.class)
class SignalRepositoryTest {

	@TestConfiguration
	@EnableJpaAuditing
	static class AuditingTestConfig {
	}

	@Autowired
	private SignalRepository repository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void roundTripsRhythmAndFieldsAndStampsAuditTimes() {
		Signal signal = Signal.createCustom("link-1", "약 먹을 시간", "저녁 약을 먹자는 뜻",
				SignalCategory.DAILY, "pill", List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG));

		Signal saved = entityManager.persistFlushFind(signal);
		entityManager.clear();
		Signal reloaded = repository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getRhythm()).containsExactly(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG);
		assertThat(reloaded.getLinkId()).isEqualTo("link-1");
		assertThat(reloaded.getName()).isEqualTo("약 먹을 시간");
		assertThat(reloaded.getSource()).isEqualTo(SignalSource.CUSTOM);
		assertThat(reloaded.getCategory()).isEqualTo(SignalCategory.DAILY);
		assertThat(reloaded.isPracticed()).isFalse();
		assertThat(reloaded.getCreatedAt()).isNotNull();
		assertThat(reloaded.getUpdatedAt()).isNotNull();
	}

	@Test
	void findsOnlyNonDeletedSignalsForALink() {
		Signal active = entityManager.persistAndFlush(
				Signal.createCustom("link-2", "활성 신호", null, SignalCategory.CHECK, "icon",
						List.of(RhythmBeat.SHORT, RhythmBeat.LONG)));
		Signal deleted = Signal.createCustom("link-2", "삭제된 신호", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG));
		deleted.softDelete();
		entityManager.persistAndFlush(deleted);

		List<Signal> result = repository.findByLinkIdAndDeletedAtIsNull("link-2");

		assertThat(result).extracting(Signal::getId).containsExactly(active.getId());
	}

	@Test
	void findsNothingForADifferentLink() {
		entityManager.persistAndFlush(
				Signal.createCustom("link-3", "다른 연결 신호", null, SignalCategory.OUTING, "icon",
						List.of(RhythmBeat.SHORT, RhythmBeat.SHORT)));

		List<Signal> result = repository.findByLinkIdAndDeletedAtIsNull("link-99");

		assertThat(result).isEmpty();
	}
}
