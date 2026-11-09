package com.sonulim.domain.signal.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.domain.signal.domain.SignalTemplate;
import com.sonulim.domain.signal.domain.SignalTemplateRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.linkaccess.LinkAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignalCommandService {

	private final SignalRepository signalRepository;
	private final SignalTemplateRepository signalTemplateRepository;
	private final LinkAccessGuard linkAccessGuard;

	public SignalCommandService(SignalRepository signalRepository, SignalTemplateRepository signalTemplateRepository,
			LinkAccessGuard linkAccessGuard) {
		this.signalRepository = signalRepository;
		this.signalTemplateRepository = signalTemplateRepository;
		this.linkAccessGuard = linkAccessGuard;
	}

	/**
	 * Idempotency-Key로 재시도하면(같은 연결·같은 키) 새로 만들지 않고 이전에 만든 신호를
	 * 그대로 돌려준다. link 도메인(IdempotencyKeys)과 같은 패턴 — 공통 처리가 아직 없어서
	 * signal 안에서 헤더를 직접 파싱한다. source로 CUSTOM(M-3)·TEMPLATE(M-2)를 가른다.
	 */
	public Signal create(UUID linkId, UUID requesterId, String role, String idempotencyKeyHeader,
			CreateSignalCommand command) {
		GuardianOnly.require(role);
		UUID idempotencyKey = IdempotencyKeys.parse(idempotencyKeyHeader);
		linkAccessGuard.verifyAccessible(linkId, requesterId);
		Optional<Signal> alreadyCreated = signalRepository.findByLinkIdAndIdempotencyKey(linkId, idempotencyKey);
		if (alreadyCreated.isPresent()) {
			return alreadyCreated.get();
		}

		Signal signal = switch (command.source()) {
			case CUSTOM -> createCustom(linkId, command);
			case TEMPLATE -> createFromTemplate(linkId, command);
		};
		signal.stampIdempotencyKey(idempotencyKey);
		return signalRepository.save(signal);
	}

	/**
	 * M-3: name·category·rhythm은 요청 DTO에서 조건부 필수(TEMPLATE일 땐 안 보냄)라 bean
	 * validation으로 표현할 수 없어 여기서 직접 검증한다.
	 */
	private Signal createCustom(UUID linkId, CreateSignalCommand command) {
		if (command.name() == null || command.name().isBlank()) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED, "이름을 입력해 주세요", "name");
		}
		if (command.category() == null) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED, "카테고리를 선택해 주세요", "category");
		}
		List<RhythmBeat> rhythm = RhythmBeat.parseAll(command.rhythm());
		ensureRhythmNotDuplicated(linkId, rhythm, null);
		return Signal.createCustom(linkId, command.name(), command.description(), command.category(),
				command.icon(), rhythm);
	}

	/**
	 * M-2: 기본 신호의 이름·뜻·카테고리·아이콘·리듬을 그대로 복사한다. 삭제한 기본 신호는
	 * 다시 추가할 수 있다는 명세대로, "이미 추가됨" 판정은 삭제 안 된 것만 센다.
	 */
	private Signal createFromTemplate(UUID linkId, CreateSignalCommand command) {
		if (command.templateId() == null) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED, "기본 신호를 선택해 주세요", "templateId");
		}
		SignalTemplate template = signalTemplateRepository.findById(command.templateId())
				.orElseThrow(() -> new SonulimException(ErrorCode.NOT_FOUND, "신호를 찾을 수 없어요"));
		if (signalRepository.existsByLinkIdAndTemplateIdAndDeletedAtIsNull(linkId, command.templateId())) {
			throw new SonulimException(ErrorCode.TEMPLATE_ALREADY_ADDED, "이미 추가한 신호예요");
		}
		ensureRhythmNotDuplicated(linkId, template.getRhythm(), null);
		return Signal.fromTemplate(linkId, command.templateId(), template.getName(), template.getDescription(),
				template.getCategory(), template.getIcon(), template.getRhythm());
	}

	@Transactional
	public Signal update(UUID pathLinkId, UUID signalId, UUID requesterId, String role, UpdateSignalCommand command) {
		GuardianOnly.require(role);
		Signal signal = findAccessibleSignal(pathLinkId, signalId, requesterId);

		if (command.rhythm() != null) {
			List<RhythmBeat> rhythm = RhythmBeat.parseAll(command.rhythm());
			ensureRhythmNotDuplicated(signal.getLinkId(), rhythm, signal.getId());
			signal.updateRhythm(rhythm);
		}
		if (command.name() != null) {
			signal.updateName(command.name());
		}
		if (command.description() != null) {
			signal.updateDescription(command.description());
		}
		if (command.category() != null) {
			signal.updateCategory(command.category());
		}
		if (command.icon() != null) {
			signal.updateIcon(command.icon());
		}
		return signal;
	}

	@Transactional
	public void delete(UUID pathLinkId, UUID signalId, UUID requesterId, String role) {
		GuardianOnly.require(role);
		Signal signal = findAccessibleSignal(pathLinkId, signalId, requesterId);
		signal.softDelete();
	}

	/**
	 * signalId로 먼저 찾고, 신호의 실제 linkId를 기준으로 접근 권한을 검증한다(경로의 linkId가
	 * 아니라) — 그래서 경로 linkId가 실제 linkId와 다르면 다른 연결의 신호를 가리키는 거라
	 * 똑같이 404로 처리한다(SignalQueryService.getDetail과 동일한 순서).
	 */
	private Signal findAccessibleSignal(UUID pathLinkId, UUID signalId, UUID requesterId) {
		Signal signal = signalRepository.findByIdAndDeletedAtIsNull(signalId)
				.orElseThrow(() -> new SonulimException(ErrorCode.NOT_FOUND, "신호를 찾을 수 없어요"));

		linkAccessGuard.verifyAccessible(signal.getLinkId(), requesterId);

		if (!signal.getLinkId().equals(pathLinkId)) {
			throw new SonulimException(ErrorCode.NOT_FOUND, "신호를 찾을 수 없어요");
		}
		return signal;
	}

	/**
	 * 공통 데이터 형식: "이 연결의 다른 신호와 리듬이 완전히 같으면" 안 된다. excludeSignalId는
	 * 수정 시 자기 자신을 중복 검사에서 빼기 위한 것(생성 시에는 null). 명세 응답 형식대로
	 * field=rhythm, details.conflictSignal={id,name}을 담는다.
	 */
	private void ensureRhythmNotDuplicated(UUID linkId, List<RhythmBeat> rhythm, UUID excludeSignalId) {
		signalRepository.findByLinkIdAndRhythm(linkId, rhythm, excludeSignalId).ifPresent(conflicting -> {
			throw new SonulimException(ErrorCode.RHYTHM_DUPLICATE,
					"'" + conflicting.getName() + "'와 같은 리듬이에요", "rhythm",
					Map.of("conflictSignal", Map.of("id", conflicting.getId(), "name", conflicting.getName())));
		});
	}
}
