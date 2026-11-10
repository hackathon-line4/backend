package com.sonulim.domain.signal.application;

import java.util.List;

import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalTemplate;
import com.sonulim.domain.signal.domain.SignalTemplateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * signal_templates 실제 데이터에 같은 리듬의 템플릿이 두 개 있으면 안 된다(#52 리뷰,
 * eunsolee) — 연결 승인 시 SignalTemplateSeeder가 바로 그 상황에서 충돌한다. 지금은
 * 실제 시드 데이터를 넣는 작업이 이 브랜치 범위 밖이라(#49) 카탈로그가 비어 있을 수
 * 있다 — 그럴 땐 비교 대상이 없어 그냥 통과하고, 나중에 실제 데이터가 들어오면 이
 * 테스트가 그 데이터를 검증하게 된다. rhythm이 없는(null) 템플릿은 이 방식으로 비교할
 * 수 없어 제외한다.
 */
@DataJpaTest
class SignalTemplateCatalogIntegrityTest {

	@Autowired
	private SignalTemplateRepository signalTemplateRepository;

	@Test
	void catalogHasNoTwoTemplatesWithTheSameRhythm() {
		List<List<RhythmBeat>> rhythms = signalTemplateRepository.findAll().stream()
				.map(SignalTemplate::getRhythm)
				.filter(rhythm -> rhythm != null && !rhythm.isEmpty())
				.toList();

		assertThat(rhythms).doesNotHaveDuplicates();
	}

	@Test
	void detectsADuplicateWhenTwoTemplatesShareARhythm() {
		SignalTemplate first = new SignalTemplate("가자", null, SignalCategory.OUTING, "walk",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG));
		SignalTemplate second = new SignalTemplate("같은 리듬", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG));

		List<List<RhythmBeat>> rhythms = List.of(first, second).stream()
				.map(SignalTemplate::getRhythm)
				.filter(rhythm -> rhythm != null && !rhythm.isEmpty())
				.toList();

		assertThatThrownBy(() -> assertThat(rhythms).doesNotHaveDuplicates())
				.isInstanceOf(AssertionError.class);
	}
}
