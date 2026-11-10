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
import com.sonulim.global.linkaccess.LinkAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SignalTemplateQueryServiceTest {

	@Mock
	private SignalTemplateRepository signalTemplateRepository;

	@Mock
	private SignalRepository signalRepository;

	@Mock
	private LinkAccessGuard linkAccessGuard;

	private final UUID requesterId = UUID.randomUUID();

	private SignalTemplateQueryService service() {
		return new SignalTemplateQueryService(signalTemplateRepository, signalRepository, linkAccessGuard);
	}

	private SignalTemplate template(String name, String description, SignalCategory category) {
		return new SignalTemplate(name, description, category, "icon", List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
	}

	@Test
	void rejectsARequesterWhoIsNotAGuardian() {
		assertThatThrownBy(() -> service().list(requesterId, "RECIPIENT", null, null, null))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.ROLE_MISMATCH);
		verifyNoInteractions(signalTemplateRepository, linkAccessGuard);
	}

	@Test
	void returnsAllTemplatesWithAlreadyAddedFalseWhenNoLinkIdIsGiven() {
		SignalTemplate gaja = template("가자", "함께 이동하자는 뜻", SignalCategory.OUTING);
		given(signalTemplateRepository.findAll()).willReturn(List.of(gaja));

		List<SignalTemplateView> result = service().list(requesterId, "GUARDIAN", null, null, null);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).template()).isEqualTo(gaja);
		assertThat(result.get(0).alreadyAdded()).isFalse();
		verifyNoInteractions(linkAccessGuard);
	}

	@Test
	void verifiesLinkAccessBeforeComputingResultsEvenWhenNothingMatches() {
		UUID linkId = UUID.randomUUID();
		given(signalTemplateRepository.findAll()).willReturn(List.of());

		List<SignalTemplateView> result = service().list(requesterId, "GUARDIAN", linkId, "없는 검색어", null);

		assertThat(result).isEmpty();
		verify(linkAccessGuard).verifyAccessible(linkId, requesterId);
	}

	@Test
	void marksAlreadyAddedTrueOnlyForTemplatesWithANonDeletedSignalInThatLink() {
		// new SignalTemplate(...)은 영속화 전엔 id가 null이라(Hibernate가 저장 시점에 채움),
		// "서로 다른 저장된 템플릿"을 구분하려고 id가 채워진 mock을 쓴다.
		UUID linkId = UUID.randomUUID();
		SignalTemplate gaja = org.mockito.Mockito.mock(SignalTemplate.class);
		given(gaja.getId()).willReturn(UUID.randomUUID());
		given(gaja.getName()).willReturn("가자");
		SignalTemplate meal = org.mockito.Mockito.mock(SignalTemplate.class);
		given(meal.getId()).willReturn(UUID.randomUUID());
		given(meal.getName()).willReturn("밥 먹자");
		given(signalTemplateRepository.findAll()).willReturn(List.of(gaja, meal));
		given(signalRepository.existsByLinkIdAndTemplateIdAndDeletedAtIsNull(linkId, gaja.getId())).willReturn(true);
		given(signalRepository.existsByLinkIdAndTemplateIdAndDeletedAtIsNull(linkId, meal.getId())).willReturn(false);

		List<SignalTemplateView> result = service().list(requesterId, "GUARDIAN", linkId, null, null);

		assertThat(result).extracting(SignalTemplateView::template, SignalTemplateView::alreadyAdded)
				.containsExactlyInAnyOrder(
						org.assertj.core.api.Assertions.tuple(gaja, true),
						org.assertj.core.api.Assertions.tuple(meal, false));
	}

	@Test
	void filtersByCategoryWhenGiven() {
		SignalTemplate gaja = template("가자", null, SignalCategory.OUTING);
		SignalTemplate meal = template("밥 먹자", null, SignalCategory.DAILY);
		given(signalTemplateRepository.findAll()).willReturn(List.of(gaja, meal));

		List<SignalTemplateView> result = service().list(requesterId, "GUARDIAN", null, null, SignalCategory.DAILY);

		assertThat(result).extracting(SignalTemplateView::template).containsExactly(meal);
	}

	@Test
	void filtersByQMatchingNameOrDescriptionCaseInsensitively() {
		SignalTemplate gaja = template("가자", "함께 이동하자는 뜻", SignalCategory.OUTING);
		SignalTemplate meal = template("밥 먹자", "식사하러 오라는 뜻", SignalCategory.DAILY);
		given(signalTemplateRepository.findAll()).willReturn(List.of(gaja, meal));

		List<SignalTemplateView> byName = service().list(requesterId, "GUARDIAN", null, "가자", null);
		List<SignalTemplateView> byDescription = service().list(requesterId, "GUARDIAN", null, "식사", null);

		assertThat(byName).extracting(SignalTemplateView::template).containsExactly(gaja);
		assertThat(byDescription).extracting(SignalTemplateView::template).containsExactly(meal);
	}
}
