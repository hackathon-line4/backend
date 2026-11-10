package com.sonulim.domain.signal.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.domain.signal.domain.SignalSource;
import com.sonulim.domain.signal.domain.SignalTemplate;
import com.sonulim.domain.signal.domain.SignalTemplateRepository;
import com.sonulim.global.exception.SonulimException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SignalTemplateSeederTest {

	@Mock
	private SignalTemplateRepository signalTemplateRepository;

	@Mock
	private SignalRepository signalRepository;

	private final UUID linkId = UUID.randomUUID();

	private SignalTemplateSeeder seeder() {
		return new SignalTemplateSeeder(signalTemplateRepository, signalRepository);
	}

	@Test
	void createsOneSignalPerTemplateNotYetSeededForThisLink() {
		SignalTemplate template = new SignalTemplate("가자", "함께 이동하자는 뜻", SignalCategory.OUTING, "walk",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG));
		given(signalTemplateRepository.findAll()).willReturn(List.of(template));
		given(signalRepository.findByLinkIdAndRhythm(any(), any(), any())).willReturn(Optional.empty());

		seeder().seedDefaults(linkId);

		ArgumentCaptor<Signal> captor = ArgumentCaptor.forClass(Signal.class);
		verify(signalRepository).save(captor.capture());
		Signal saved = captor.getValue();
		assertThat(saved.getLinkId()).isEqualTo(linkId);
		assertThat(saved.getSource()).isEqualTo(SignalSource.TEMPLATE);
		assertThat(saved.getName()).isEqualTo("가자");
		assertThat(saved.getCategory()).isEqualTo(SignalCategory.OUTING);
		assertThat(saved.getRhythm()).containsExactly(RhythmBeat.LONG, RhythmBeat.LONG);
	}

	@Test
	void skipsATemplateAlreadySeededForThisLink() {
		SignalTemplate template = new SignalTemplate("가자", null, SignalCategory.OUTING, "walk",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG));
		given(signalTemplateRepository.findAll()).willReturn(List.of(template));
		given(signalRepository.existsByLinkIdAndTemplateId(eq(linkId), any())).willReturn(true);

		seeder().seedDefaults(linkId);

		verify(signalRepository, never()).save(any());
	}

	@Test
	void doesNothingWhenTheTemplateCatalogIsEmpty() {
		given(signalTemplateRepository.findAll()).willReturn(List.of());

		seeder().seedDefaults(linkId);

		verify(signalRepository, never()).save(any());
	}

	/**
	 * 새로 승인된 연결은 이 시점에 신호가 하나도 없으므로, 실제로는 카탈로그 자체에 같은
	 * 리듬의 템플릿이 두 개 있을 때만 이 충돌이 난다 — 즉 보호자 입력 문제가 아니라
	 * signal_templates 데이터 버그다. 그래서 승인 API에 RHYTHM_DUPLICATE를 그대로
	 * 노출하지 않고 내부 오류로 처리한다(은솔님 PR #52 리뷰).
	 */
	@Test
	void treatsATemplateRhythmCollisionAsAnInternalErrorInsteadOfLeakingRhythmDuplicate() {
		SignalTemplate template = new SignalTemplate("가자", null, SignalCategory.OUTING, "walk",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG));
		Signal conflicting = Signal.createCustom(linkId, "다른 이름", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG));
		given(signalTemplateRepository.findAll()).willReturn(List.of(template));
		given(signalRepository.findByLinkIdAndRhythm(linkId, List.of(RhythmBeat.LONG, RhythmBeat.LONG), null))
				.willReturn(Optional.of(conflicting));

		assertThatThrownBy(() -> seeder().seedDefaults(linkId))
				.isInstanceOf(IllegalStateException.class)
				.isNotInstanceOf(SonulimException.class);
		verify(signalRepository, never()).save(any());
	}

	@Test
	void seedsOnlyTheTemplatesNotAlreadyPresentWhenCalledAgain() {
		// new SignalTemplate(...)은 영속화 전엔 id가 null이라(Hibernate가 저장 시점에 채움),
		// "이미 시드 데이터로 저장돼 있다"는 상황을 흉내 내려고 id가 채워진 mock을 쓴다.
		SignalTemplate seeded = org.mockito.Mockito.mock(SignalTemplate.class);
		given(seeded.getId()).willReturn(UUID.randomUUID());
		SignalTemplate notYetSeeded = org.mockito.Mockito.mock(SignalTemplate.class);
		given(notYetSeeded.getId()).willReturn(UUID.randomUUID());
		given(notYetSeeded.getName()).willReturn("밥 먹자");
		given(notYetSeeded.getCategory()).willReturn(SignalCategory.DAILY);
		given(notYetSeeded.getIcon()).willReturn("meal");
		given(notYetSeeded.getRhythm()).willReturn(List.of(RhythmBeat.SHORT, RhythmBeat.SHORT));
		given(signalTemplateRepository.findAll()).willReturn(List.of(seeded, notYetSeeded));
		given(signalRepository.existsByLinkIdAndTemplateId(linkId, seeded.getId())).willReturn(true);
		given(signalRepository.existsByLinkIdAndTemplateId(linkId, notYetSeeded.getId())).willReturn(false);
		given(signalRepository.findByLinkIdAndRhythm(any(), any(), any())).willReturn(Optional.empty());

		seeder().seedDefaults(linkId);

		verify(signalRepository, times(1)).save(any());
	}
}
