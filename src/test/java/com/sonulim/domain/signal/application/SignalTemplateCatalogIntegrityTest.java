package com.sonulim.domain.signal.application;

import java.util.List;

import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalTemplate;
import com.sonulim.domain.signal.domain.SignalTemplateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * signal_templates 실제 데이터에 같은 진동(pulses)의 템플릿이 두 개 있으면 안 된다(#52
 * 리뷰, eunsolee) — 연결 승인 시 SignalTemplateSeeder가 바로 그 상황에서 충돌한다.
 * rhythm이 아니라 pulses로 비교한다 — 기본 신호 6종은 전부 rhythm이 null이라(#61) rhythm
 * 기준으로는 비교 자체가 안 된다. SignalTemplateCatalogSeeder를 직접 호출해 실제로
 * 서비스가 채우는 카탈로그를 검증한다(이미 채워져 있으면 그대로 둔다).
 */
@DataJpaTest
class SignalTemplateCatalogIntegrityTest {

	@Autowired
	private SignalTemplateRepository signalTemplateRepository;

	@Test
	void realCatalogHasNoTwoTemplatesWithTheSamePulses() throws Exception {
		new SignalTemplateCatalogSeeder(signalTemplateRepository).run(null);

		List<List<Integer>> pulses = signalTemplateRepository.findAll().stream()
				.map(SignalTemplate::getPulses)
				.toList();

		assertThat(pulses).hasSize(6);
		assertThat(pulses).doesNotHaveDuplicates();
	}

	@Test
	void detectsADuplicateWhenTwoTemplatesSharePulses() {
		SignalTemplate first = new SignalTemplate("가자", null, SignalCategory.OUTING, "walk", null,
				List.of(1000, 1000));
		SignalTemplate second = new SignalTemplate("같은 진동", null, SignalCategory.CHECK, "icon", null,
				List.of(1000, 1000));

		List<List<Integer>> pulses = List.of(first, second).stream()
				.map(SignalTemplate::getPulses)
				.toList();

		assertThatThrownBy(() -> assertThat(pulses).doesNotHaveDuplicates())
				.isInstanceOf(AssertionError.class);
	}
}
