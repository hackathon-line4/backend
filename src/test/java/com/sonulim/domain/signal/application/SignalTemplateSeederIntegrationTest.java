package com.sonulim.domain.signal.application;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.domain.signal.domain.SignalTemplate;
import com.sonulim.domain.signal.domain.SignalTemplateRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SignalTemplateSeederTest(Mockito)는 각 메서드 호출을 검증하지만 실제 트랜잭션
 * 롤백까지는 흉내 낼 수 없다 — 이 테스트는 실제 DB로 "실패 시 일부만 저장되지 않음"을
 * 확인한다(완료 조건: 실패 시 저장 일관성).
 */
@SpringBootTest
class SignalTemplateSeederIntegrationTest {

	@Autowired
	private SignalTemplateSeeder seeder;

	@Autowired
	private SignalTemplateRepository signalTemplateRepository;

	@Autowired
	private SignalRepository signalRepository;

	@AfterEach
	void cleanUp() {
		signalRepository.deleteAll();
		signalTemplateRepository.deleteAll();
	}

	@Test
	void createsASignalForEveryTemplateInTheCatalog() {
		signalTemplateRepository.saveAndFlush(new SignalTemplate("가자", "함께 이동하자는 뜻", SignalCategory.OUTING, "walk",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG)));
		signalTemplateRepository.saveAndFlush(new SignalTemplate("밥 먹자", "식사하러 오라는 뜻", SignalCategory.DAILY, "meal",
				List.of(RhythmBeat.SHORT, RhythmBeat.SHORT)));
		UUID linkId = UUID.randomUUID();

		seeder.seedDefaults(linkId);

		assertThat(signalRepository.findByLinkIdAndDeletedAtIsNull(linkId)).hasSize(2)
				.extracting("name").containsExactlyInAnyOrder("가자", "밥 먹자");
	}

	@Test
	void callingItAgainDoesNotCreateDuplicateSignals() {
		signalTemplateRepository.saveAndFlush(new SignalTemplate("가자", null, SignalCategory.OUTING, "walk",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG)));
		UUID linkId = UUID.randomUUID();
		seeder.seedDefaults(linkId);

		seeder.seedDefaults(linkId);

		assertThat(signalRepository.findByLinkIdAndDeletedAtIsNull(linkId)).hasSize(1);
	}

	@Test
	void rollsBackAllSignalsForThisLinkWhenATemplateRhythmCollisionIsHitMidway() {
		signalTemplateRepository.saveAndFlush(new SignalTemplate("가자", null, SignalCategory.OUTING, "walk",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG)));
		signalTemplateRepository.saveAndFlush(new SignalTemplate("같은 리듬", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG)));
		UUID linkId = UUID.randomUUID();

		assertThatThrownBy(() -> seeder.seedDefaults(linkId))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.RHYTHM_DUPLICATE);

		assertThat(signalRepository.findByLinkIdAndDeletedAtIsNull(linkId)).isEmpty();
	}
}
