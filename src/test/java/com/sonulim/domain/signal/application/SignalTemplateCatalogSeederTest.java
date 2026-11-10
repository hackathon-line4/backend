package com.sonulim.domain.signal.application;

import java.util.List;

import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalTemplate;
import com.sonulim.domain.signal.domain.SignalTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SignalTemplateCatalogSeederTest {

	@Mock
	private SignalTemplateRepository signalTemplateRepository;

	private SignalTemplateCatalogSeeder seeder() {
		return new SignalTemplateCatalogSeeder(signalTemplateRepository);
	}

	@Test
	void seedsTheSixMvpTemplatesWhenTheCatalogIsEmpty() throws Exception {
		given(signalTemplateRepository.count()).willReturn(0L);

		seeder().run(null);

		ArgumentCaptor<List<SignalTemplate>> captor = ArgumentCaptor.forClass(List.class);
		verify(signalTemplateRepository).saveAll(captor.capture());
		List<SignalTemplate> saved = captor.getValue();
		assertThat(saved).extracting(SignalTemplate::getName)
				.containsExactlyInAnyOrder("나 여기 있어", "밥 먹자", "가자", "기다려", "누가 왔어", "위험해");
		assertThat(saved).allSatisfy(template -> assertThat(template.getRhythm()).isNull());

		assertThat(templateNamed(saved, "나 여기 있어").getPulses()).containsExactly(150, 150, 150);
		assertThat(templateNamed(saved, "나 여기 있어").getCategory()).isEqualTo(SignalCategory.DAILY);
		assertThat(templateNamed(saved, "밥 먹자").getPulses()).containsExactly(700);
		assertThat(templateNamed(saved, "밥 먹자").getCategory()).isEqualTo(SignalCategory.DAILY);
		assertThat(templateNamed(saved, "가자").getPulses()).containsExactly(150, 150, 150, 150, 150, 150, 150);
		assertThat(templateNamed(saved, "가자").getCategory()).isEqualTo(SignalCategory.OUTING);
		assertThat(templateNamed(saved, "기다려").getPulses()).containsExactly(350, 50, 350, 50, 350, 50, 350);
		assertThat(templateNamed(saved, "기다려").getCategory()).isEqualTo(SignalCategory.OUTING);
		assertThat(templateNamed(saved, "누가 왔어").getPulses())
				.containsExactly(150, 150, 150, 150, 150, 150, 150, 500, 700);
		assertThat(templateNamed(saved, "누가 왔어").getCategory()).isEqualTo(SignalCategory.OUTING);
		assertThat(templateNamed(saved, "위험해").getPulses())
				.containsExactly(1000, 150, 1000, 500, 1000, 150, 1000, 500, 1000, 150, 1000);
		assertThat(templateNamed(saved, "위험해").getCategory()).isEqualTo(SignalCategory.EMERGENCY);
	}

	@Test
	void doesNothingWhenTheCatalogAlreadyHasTemplates() throws Exception {
		given(signalTemplateRepository.count()).willReturn(1L);

		seeder().run(null);

		verify(signalTemplateRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
	}

	private SignalTemplate templateNamed(List<SignalTemplate> templates, String name) {
		return templates.stream().filter(t -> t.getName().equals(name)).findFirst().orElseThrow();
	}
}
