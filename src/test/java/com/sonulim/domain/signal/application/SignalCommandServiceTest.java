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
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.linkaccess.LinkAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SignalCommandServiceTest {

	@Mock
	private SignalRepository signalRepository;

	@Mock
	private SignalTemplateRepository signalTemplateRepository;

	@Mock
	private LinkAccessGuard linkAccessGuard;

	private final UUID linkId = UUID.randomUUID();
	private final UUID requesterId = UUID.randomUUID();
	private final UUID idempotencyKey = UUID.randomUUID();
	private final String idempotencyKeyHeader = idempotencyKey.toString();

	private SignalCommandService service() {
		return new SignalCommandService(signalRepository, signalTemplateRepository, linkAccessGuard);
	}

	private static CreateSignalCommand customCommand(String name, SignalCategory category, List<String> rhythm) {
		return new CreateSignalCommand(SignalSource.CUSTOM, null, name, null, category, "icon", rhythm);
	}

	private static CreateSignalCommand templateCommand(UUID templateId) {
		return new CreateSignalCommand(SignalSource.TEMPLATE, templateId, null, null, null, null, null);
	}

	// ---- create: CUSTOM ----

	@Test
	void createVerifiesAccessValidatesRhythmAndSaves() {
		SignalCommandService service = service();
		given(signalRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		Signal result = service.create(linkId, requesterId, "GUARDIAN", idempotencyKeyHeader,
				customCommand("이름", SignalCategory.CHECK, List.of("SHORT", "LONG")));

		verify(linkAccessGuard).verifyAccessible(linkId, requesterId);
		assertThat(result.getName()).isEqualTo("이름");
		assertThat(result.getRhythm()).containsExactly(RhythmBeat.SHORT, RhythmBeat.LONG);
	}

	@Test
	void createRejectsARequesterWhoIsNotAGuardianBeforeTouchingTheGuardOrRepository() {
		SignalCommandService service = service();

		assertThatThrownBy(() -> service.create(linkId, requesterId, "RECIPIENT", idempotencyKeyHeader,
				customCommand("이름", SignalCategory.CHECK, List.of("SHORT", "LONG"))))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.ROLE_MISMATCH);
		verifyNoInteractions(linkAccessGuard, signalRepository, signalTemplateRepository);
	}

	@Test
	void createRejectsInvalidRhythmBeforeTouchingTheRepository() {
		SignalCommandService service = service();

		assertThatThrownBy(() -> service.create(linkId, requesterId, "GUARDIAN", idempotencyKeyHeader,
				customCommand("이름", SignalCategory.CHECK, List.of("SHORT"))))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.RHYTHM_INVALID);
		verify(signalRepository, never()).save(any());
	}

	@Test
	void createRejectsABlankNameForCustom() {
		SignalCommandService service = service();

		assertThatThrownBy(() -> service.create(linkId, requesterId, "GUARDIAN", idempotencyKeyHeader,
				customCommand("", SignalCategory.CHECK, List.of("SHORT", "LONG"))))
				.isInstanceOf(SonulimException.class)
				.satisfies(e -> {
					SonulimException exception = (SonulimException) e;
					assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
					assertThat(exception.getField()).isEqualTo("name");
				});
		verify(signalRepository, never()).save(any());
	}

	@Test
	void createRejectsAMissingCategoryForCustom() {
		SignalCommandService service = service();

		assertThatThrownBy(() -> service.create(linkId, requesterId, "GUARDIAN", idempotencyKeyHeader,
				customCommand("이름", null, List.of("SHORT", "LONG"))))
				.isInstanceOf(SonulimException.class)
				.satisfies(e -> {
					SonulimException exception = (SonulimException) e;
					assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
					assertThat(exception.getField()).isEqualTo("category");
				});
		verify(signalRepository, never()).save(any());
	}

	@Test
	void createRejectsARhythmThatDuplicatesAnExistingSignalInTheSameLink() {
		SignalCommandService service = service();
		// 영속화 전 Signal은 id가 null이라(Hibernate가 저장 시점에 채움), details.conflictSignal.id를
		// 검증하려고 id가 채워진 mock으로 "이미 저장된 신호"를 흉내 낸다.
		UUID conflictId = UUID.randomUUID();
		Signal existing = org.mockito.Mockito.mock(Signal.class);
		given(existing.getId()).willReturn(conflictId);
		given(existing.getName()).willReturn("기존");
		given(signalRepository.findByLinkIdAndRhythm(linkId, List.of(RhythmBeat.SHORT, RhythmBeat.LONG), null))
				.willReturn(Optional.of(existing));

		assertThatThrownBy(() -> service.create(linkId, requesterId, "GUARDIAN", idempotencyKeyHeader,
				customCommand("새 이름", SignalCategory.CHECK, List.of("SHORT", "LONG"))))
				.isInstanceOf(SonulimException.class)
				.satisfies(e -> {
					SonulimException exception = (SonulimException) e;
					assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RHYTHM_DUPLICATE);
					assertThat(exception.getField()).isEqualTo("rhythm");
					@SuppressWarnings("unchecked")
					var conflictSignal = (java.util.Map<String, Object>) exception.getDetails().get("conflictSignal");
					assertThat(conflictSignal).containsEntry("id", conflictId).containsEntry("name", "기존");
				});
		verify(signalRepository, never()).save(any());
	}

	@Test
	void createReturnsTheExistingSignalWhenRetriedWithTheSameIdempotencyKeyWithoutSavingAgain() {
		SignalCommandService service = service();
		Signal alreadyCreated = org.mockito.Mockito.mock(Signal.class);
		given(signalRepository.findByLinkIdAndIdempotencyKey(linkId, idempotencyKey))
				.willReturn(Optional.of(alreadyCreated));

		Signal result = service.create(linkId, requesterId, "GUARDIAN", idempotencyKeyHeader,
				customCommand("이름", SignalCategory.CHECK, List.of("SHORT", "LONG")));

		assertThat(result).isSameAs(alreadyCreated);
		verify(signalRepository, never()).save(any());
	}

	@Test
	void createRequiresTheIdempotencyKeyHeaderBeforeTouchingTheGuardOrRepository() {
		SignalCommandService service = service();

		assertThatThrownBy(() -> service.create(linkId, requesterId, "GUARDIAN", null,
				customCommand("이름", SignalCategory.CHECK, List.of("SHORT", "LONG"))))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
		verifyNoInteractions(linkAccessGuard, signalRepository, signalTemplateRepository);
	}

	@Test
	void createRejectsAnIdempotencyKeyThatIsNotAValidUuid() {
		SignalCommandService service = service();

		assertThatThrownBy(() -> service.create(linkId, requesterId, "GUARDIAN", "not-a-uuid",
				customCommand("이름", SignalCategory.CHECK, List.of("SHORT", "LONG"))))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.VALIDATION_FAILED);
	}

	// ---- create: TEMPLATE (M-2) ----

	@Test
	void createFromTemplateCopiesTheTemplatesFieldsAndSaves() {
		SignalCommandService service = service();
		UUID templateId = UUID.randomUUID();
		SignalTemplate template = new SignalTemplate("가자", "함께 이동하자는 뜻", SignalCategory.OUTING, "walk",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG));
		given(signalTemplateRepository.findById(templateId)).willReturn(Optional.of(template));
		given(signalRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		Signal result = service.create(linkId, requesterId, "GUARDIAN", idempotencyKeyHeader,
				templateCommand(templateId));

		assertThat(result.getSource()).isEqualTo(SignalSource.TEMPLATE);
		assertThat(result.getName()).isEqualTo("가자");
		assertThat(result.getCategory()).isEqualTo(SignalCategory.OUTING);
		assertThat(result.getRhythm()).containsExactly(RhythmBeat.LONG, RhythmBeat.LONG);
		assertThat(result.isPracticed()).isFalse();
	}

	@Test
	void createFromTemplateRejectsAMissingTemplateIdBeforeTouchingTheRepository() {
		SignalCommandService service = service();

		assertThatThrownBy(() -> service.create(linkId, requesterId, "GUARDIAN", idempotencyKeyHeader,
				templateCommand(null)))
				.isInstanceOf(SonulimException.class)
				.satisfies(e -> {
					SonulimException exception = (SonulimException) e;
					assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
					assertThat(exception.getField()).isEqualTo("templateId");
				});
		verifyNoInteractions(signalTemplateRepository);
		verify(signalRepository, never()).save(any());
	}

	@Test
	void createFromTemplateReturnsNotFoundWhenTheTemplateDoesNotExist() {
		SignalCommandService service = service();
		UUID templateId = UUID.randomUUID();
		given(signalTemplateRepository.findById(templateId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(linkId, requesterId, "GUARDIAN", idempotencyKeyHeader,
				templateCommand(templateId)))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
		verify(signalRepository, never()).save(any());
	}

	@Test
	void createFromTemplateRejectsATemplateAlreadyAddedToThisLink() {
		SignalCommandService service = service();
		UUID templateId = UUID.randomUUID();
		SignalTemplate template = new SignalTemplate("가자", null, SignalCategory.OUTING, "walk",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG));
		given(signalTemplateRepository.findById(templateId)).willReturn(Optional.of(template));
		given(signalRepository.existsByLinkIdAndTemplateIdAndDeletedAtIsNull(linkId, templateId)).willReturn(true);

		assertThatThrownBy(() -> service.create(linkId, requesterId, "GUARDIAN", idempotencyKeyHeader,
				templateCommand(templateId)))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.TEMPLATE_ALREADY_ADDED);
		verify(signalRepository, never()).save(any());
	}

	@Test
	void createFromTemplateAllowsReAddingAPreviouslyDeletedTemplateSignal() {
		// 명세: "삭제한 기본 신호는 다시 추가할 수 있다" — existsByLinkIdAndTemplateIdAndDeletedAtIsNull
		// 이 false(삭제된 것만 있음)이면 TEMPLATE_ALREADY_ADDED를 던지지 않아야 한다.
		SignalCommandService service = service();
		UUID templateId = UUID.randomUUID();
		SignalTemplate template = new SignalTemplate("가자", null, SignalCategory.OUTING, "walk",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG));
		given(signalTemplateRepository.findById(templateId)).willReturn(Optional.of(template));
		given(signalRepository.existsByLinkIdAndTemplateIdAndDeletedAtIsNull(linkId, templateId)).willReturn(false);
		given(signalRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		Signal result = service.create(linkId, requesterId, "GUARDIAN", idempotencyKeyHeader,
				templateCommand(templateId));

		assertThat(result.getName()).isEqualTo("가자");
	}

	@Test
	void createFromTemplateRejectsARhythmThatDuplicatesAnExistingSignalInTheSameLink() {
		SignalCommandService service = service();
		UUID templateId = UUID.randomUUID();
		SignalTemplate template = new SignalTemplate("가자", null, SignalCategory.OUTING, "walk",
				List.of(RhythmBeat.LONG, RhythmBeat.LONG));
		Signal conflicting = org.mockito.Mockito.mock(Signal.class);
		given(conflicting.getId()).willReturn(UUID.randomUUID());
		given(conflicting.getName()).willReturn("기존");
		given(signalTemplateRepository.findById(templateId)).willReturn(Optional.of(template));
		given(signalRepository.findByLinkIdAndRhythm(linkId, List.of(RhythmBeat.LONG, RhythmBeat.LONG), null))
				.willReturn(Optional.of(conflicting));

		assertThatThrownBy(() -> service.create(linkId, requesterId, "GUARDIAN", idempotencyKeyHeader,
				templateCommand(templateId)))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.RHYTHM_DUPLICATE);
		verify(signalRepository, never()).save(any());
	}

	// ---- update ----

	@Test
	void updateLooksUpByIdVerifiesAccessOnRealLinkAndAppliesOnlyGivenFields() {
		SignalCommandService service = service();
		UUID signalId = UUID.randomUUID();
		Signal signal = Signal.createCustom(linkId, "이름", "설명", SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
		given(signalRepository.findByIdAndDeletedAtIsNull(signalId)).willReturn(Optional.of(signal));

		Signal result = service.update(linkId, signalId, requesterId, "GUARDIAN",
				new UpdateSignalCommand("새 이름", null, null, null, null));

		verify(linkAccessGuard).verifyAccessible(linkId, requesterId);
		assertThat(result.getName()).isEqualTo("새 이름");
		assertThat(result.getDescription()).isEqualTo("설명");
	}

	@Test
	void updateRejectsARequesterWhoIsNotAGuardianBeforeLookingUpTheSignal() {
		SignalCommandService service = service();

		assertThatThrownBy(() -> service.update(linkId, UUID.randomUUID(), requesterId, "RECIPIENT",
				new UpdateSignalCommand("새 이름", null, null, null, null)))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.ROLE_MISMATCH);
		verifyNoInteractions(linkAccessGuard, signalRepository);
	}

	@Test
	void updateExcludesTheSignalItselfFromTheDuplicateRhythmCheck() {
		// Signal.createCustom()은 영속화 전엔 id가 null이라(Hibernate가 저장 시점에 채움),
		// "실제 저장된 신호를 조회했다"는 상황을 흉내 내려고 id가 채워진 mock을 쓴다.
		SignalCommandService service = service();
		UUID signalId = UUID.randomUUID();
		Signal signal = org.mockito.Mockito.mock(Signal.class);
		given(signal.getId()).willReturn(signalId);
		given(signal.getLinkId()).willReturn(linkId);
		given(signalRepository.findByIdAndDeletedAtIsNull(signalId)).willReturn(Optional.of(signal));

		Signal result = service.update(linkId, signalId, requesterId, "GUARDIAN",
				new UpdateSignalCommand(null, null, null, null, List.of("SHORT", "LONG")));

		assertThat(result).isSameAs(signal);
		verify(signal).updateRhythm(List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
	}

	@Test
	void updateRejectsARhythmThatDuplicatesAnotherSignalInTheSameLink() {
		SignalCommandService service = service();
		UUID signalId = UUID.randomUUID();
		Signal target = Signal.createCustom(linkId, "대상", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.SHORT));
		// 영속화 전 Signal은 id가 null이라(Hibernate가 저장 시점에 채움), "실제 저장된 다른
		// 신호가 조회됐다"는 상황을 흉내 내려고 id가 채워진 mock을 쓴다.
		Signal other = org.mockito.Mockito.mock(Signal.class);
		given(other.getId()).willReturn(UUID.randomUUID());
		given(other.getName()).willReturn("다른 신호");
		given(signalRepository.findByIdAndDeletedAtIsNull(signalId)).willReturn(Optional.of(target));
		given(signalRepository.findByLinkIdAndRhythm(linkId, List.of(RhythmBeat.LONG, RhythmBeat.LONG), null))
				.willReturn(Optional.of(other));

		assertThatThrownBy(() -> service.update(linkId, signalId, requesterId, "GUARDIAN",
				new UpdateSignalCommand(null, null, null, null, List.of("LONG", "LONG"))))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.RHYTHM_DUPLICATE);
	}

	@Test
	void updateReturnsNotFoundWhenSignalDoesNotExist() {
		SignalCommandService service = service();
		UUID signalId = UUID.randomUUID();
		given(signalRepository.findByIdAndDeletedAtIsNull(signalId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(linkId, signalId, requesterId, "GUARDIAN",
				new UpdateSignalCommand("이름", null, null, null, null)))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void updateReturnsNotFoundWhenPathLinkIdDoesNotMatchTheSignalsRealLink() {
		SignalCommandService service = service();
		UUID signalId = UUID.randomUUID();
		UUID actualLinkId = UUID.randomUUID();
		Signal signal = Signal.createCustom(actualLinkId, "이름", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
		given(signalRepository.findByIdAndDeletedAtIsNull(signalId)).willReturn(Optional.of(signal));

		assertThatThrownBy(() -> service.update(linkId, signalId, requesterId, "GUARDIAN",
				new UpdateSignalCommand("이름", null, null, null, null)))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void updateRejectsNameChangeOnATemplateSignal() {
		SignalCommandService service = service();
		UUID signalId = UUID.randomUUID();
		Signal signal = Signal.fromTemplate(linkId, UUID.randomUUID(), "기본 신호", null,
				SignalCategory.CHECK, "icon", List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
		given(signalRepository.findByIdAndDeletedAtIsNull(signalId)).willReturn(Optional.of(signal));

		assertThatThrownBy(() -> service.update(linkId, signalId, requesterId, "GUARDIAN",
				new UpdateSignalCommand("바꾼 이름", null, null, null, null)))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.SIGNAL_NOT_EDITABLE);
	}

	// ---- delete ----

	@Test
	void deleteSoftDeletesAfterVerifyingAccessAndPathLinkMatch() {
		SignalCommandService service = service();
		UUID signalId = UUID.randomUUID();
		Signal signal = Signal.createCustom(linkId, "이름", null, SignalCategory.CHECK, "icon",
				List.of(RhythmBeat.SHORT, RhythmBeat.LONG));
		given(signalRepository.findByIdAndDeletedAtIsNull(signalId)).willReturn(Optional.of(signal));

		service.delete(linkId, signalId, requesterId, "GUARDIAN");

		verify(linkAccessGuard).verifyAccessible(linkId, requesterId);
		assertThat(signal.getDeletedAt()).isNotNull();
	}

	@Test
	void deleteReturnsNotFoundWhenSignalDoesNotExist() {
		SignalCommandService service = service();
		UUID signalId = UUID.randomUUID();
		given(signalRepository.findByIdAndDeletedAtIsNull(signalId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(linkId, signalId, requesterId, "GUARDIAN"))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void deleteRejectsARequesterWhoIsNotAGuardianBeforeLookingUpTheSignal() {
		SignalCommandService service = service();

		assertThatThrownBy(() -> service.delete(linkId, UUID.randomUUID(), requesterId, "RECIPIENT"))
				.isInstanceOf(SonulimException.class)
				.extracting(e -> ((SonulimException) e).getErrorCode())
				.isEqualTo(ErrorCode.ROLE_MISMATCH);
		verifyNoInteractions(linkAccessGuard, signalRepository);
	}
}
