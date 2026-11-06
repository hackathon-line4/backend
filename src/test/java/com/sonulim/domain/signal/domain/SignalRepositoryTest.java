package com.sonulim.domain.signal.domain;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest}는 일반 @Configuration(global.JpaAuditingConfig)을 불러오지 않으므로
 * 이 테스트 안에서 {@code @EnableJpaAuditing}을 따로 켠다(BaseEntityTest와 동일한 패턴).
 */
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
		UUID linkId = UUID.randomUUID();
		Signal signal = Signal.createCustom(linkId, "약 먹을 시간", "저녁 약을 먹자는 뜻",
				SignalCategory.DAILY, "pill", List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG));

		Signal saved = entityManager.persistFlushFind(signal);
		entityManager.clear();
		Signal reloaded = repository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getRhythm()).containsExactly(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG);
		assertThat(reloaded.getLinkId()).isEqualTo(linkId);
		assertThat(reloaded.getName()).isEqualTo("약 먹을 시간");
		assertThat(reloaded.getSource()).isEqualTo(SignalSource.CUSTOM);
		assertThat(reloaded.getCategory()).isEqualTo(SignalCategory.DAILY);
		assertThat(reloaded.isPracticed()).isFalse();
		assertThat(reloaded.getCreatedAt()).isNotNull();
		assertThat(reloaded.getUpdatedAt()).isNotNull();
	}

	@Test
	void findsOnlyNonDeletedSignalsForALink() {
		UUID linkId = UUID.randomUUID();
		Signal active = entityManager.persistAndFlush(
				Signal.createCustom(linkId, "활성 신호", null, SignalCategory.CHECK, "icon",
						List.of(RhythmBeat.SHORT, RhythmBeat.LONG)));
		Signal deleted = Signal.createCustom(linkId, "삭제된 신호", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG));
		deleted.softDelete();
		entityManager.persistAndFlush(deleted);

		List<Signal> result = repository.findByLinkIdAndDeletedAtIsNull(linkId);

		assertThat(result).extracting(Signal::getId).containsExactly(active.getId());
	}

	@Test
	void findsNothingForADifferentLink() {
		UUID linkId = UUID.randomUUID();
		entityManager.persistAndFlush(
				Signal.createCustom(linkId, "다른 연결 신호", null, SignalCategory.OUTING, "icon",
						List.of(RhythmBeat.SHORT, RhythmBeat.SHORT)));

		List<Signal> result = repository.findByLinkIdAndDeletedAtIsNull(UUID.randomUUID());

		assertThat(result).isEmpty();
	}
}
