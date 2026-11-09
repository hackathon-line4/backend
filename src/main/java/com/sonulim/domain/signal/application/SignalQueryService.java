package com.sonulim.domain.signal.application;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.linkaccess.LinkAccessGuard;
import org.springframework.stereotype.Service;

@Service
public class SignalQueryService {

	private final SignalRepository signalRepository;
	private final LinkAccessGuard linkAccessGuard;

	public SignalQueryService(SignalRepository signalRepository, LinkAccessGuard linkAccessGuard) {
		this.signalRepository = signalRepository;
		this.linkAccessGuard = linkAccessGuard;
	}

	/**
	 * 명세 정렬 규칙: 카테고리 순서(일상 → 외출 → 확인 → 긴급, SignalCategory 선언 순과 일치),
	 * 그 안에서 만든 순(오래된 것부터). category를 주면 그 카테고리만 거른다.
	 */
	public List<Signal> list(UUID linkId, UUID requesterId, String role, SignalCategory category) {
		GuardianOnly.require(role);
		linkAccessGuard.verifyAccessible(linkId, requesterId);
		return signalRepository.findByLinkIdAndDeletedAtIsNull(linkId).stream()
				.filter(signal -> category == null || signal.getCategory() == category)
				.sorted(Comparator.comparing(Signal::getCategory).thenComparing(Signal::getCreatedAt))
				.toList();
	}

	/**
	 * signalId로 먼저 찾고, 그 신호의 실제 linkId를 기준으로 접근 권한을 검증한다(경로의
	 * linkId가 아니라). 그래서 접근 권한이 있어도 경로의 linkId가 신호의 실제 linkId와
	 * 다르면 다른 연결의 신호를 가리키는 거라 똑같이 404로 처리한다.
	 */
	public Signal getDetail(UUID pathLinkId, UUID signalId, UUID requesterId, String role) {
		GuardianOnly.require(role);
		Signal signal = signalRepository.findByIdAndDeletedAtIsNull(signalId)
				.orElseThrow(() -> new SonulimException(ErrorCode.NOT_FOUND, "신호를 찾을 수 없어요"));

		linkAccessGuard.verifyAccessible(signal.getLinkId(), requesterId);

		if (!signal.getLinkId().equals(pathLinkId)) {
			throw new SonulimException(ErrorCode.NOT_FOUND, "신호를 찾을 수 없어요");
		}

		return signal;
	}

	/**
	 * M-3 직접 만들기: 저장 전에 같은 연결 안에 동일한 리듬이 있는지 미리 알려준다. 생성 API의
	 * 409 RHYTHM_DUPLICATE와 달리, 중복이어도 에러가 아니라 정상 응답(valid: false)이다 —
	 * 저장을 막을지는 프론트가 이 값을 보고 판단한다. excludeSignalId는 수정 중인 신호를
	 * 비교에서 빼기 위한 것(새로 만들 때는 null).
	 */
	public RhythmCheckResult checkRhythm(UUID linkId, UUID requesterId, String role, List<String> rhythmTokens,
			UUID excludeSignalId) {
		GuardianOnly.require(role);
		linkAccessGuard.verifyAccessible(linkId, requesterId);
		List<RhythmBeat> rhythm = RhythmBeat.parseAll(rhythmTokens);
		List<Integer> pulses = RhythmBeat.toPulses(rhythm);
		long durationMs = pulses.stream().mapToLong(Integer::longValue).sum();
		return signalRepository.findByLinkIdAndPulses(linkId, pulses, excludeSignalId)
				.map(conflicting -> new RhythmCheckResult(false, durationMs,
						new RhythmCheckResult.ConflictSignal(conflicting.getId(), conflicting.getName())))
				.orElseGet(() -> new RhythmCheckResult(true, durationMs, null));
	}
}
