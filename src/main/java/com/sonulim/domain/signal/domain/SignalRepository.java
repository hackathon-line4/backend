package com.sonulim.domain.signal.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalRepository extends JpaRepository<Signal, UUID> {

	List<Signal> findByLinkIdAndDeletedAtIsNull(UUID linkId);

	Optional<Signal> findByIdAndDeletedAtIsNull(UUID id);

	/**
	 * 기본 신호 초기화(DefaultSignalSeeder)가 같은 연결에 같은 템플릿을 다시 넣지 않도록
	 * 재호출 여부를 판단할 때 쓴다 — 삭제된 것도 "이미 넣었던 적 있음"으로 친다(사용자가
	 * 지운 걸 자동 초기화가 되살리지 않도록).
	 */
	boolean existsByLinkIdAndTemplateId(UUID linkId, UUID templateId);

	/**
	 * POST /signals의 Idempotency-Key 재시도 판별용 — 같은 연결·같은 키로 재시도하면
	 * 새로 만들지 않고 이전에 만든 것을 그대로 돌려준다.
	 */
	Optional<Signal> findByLinkIdAndIdempotencyKey(UUID linkId, UUID idempotencyKey);

	/**
	 * 공통 데이터 형식: "이 연결의 다른 신호와 리듬이 완전히 같으면" 중복이다. excludeId를 주면
	 * 그 신호 자신은 비교 대상에서 뺀다(수정 시 자기 자신 제외용, 생성/확인 시에는 null).
	 */
	default Optional<Signal> findByLinkIdAndRhythm(UUID linkId, List<RhythmBeat> rhythm, UUID excludeId) {
		return findByLinkIdAndDeletedAtIsNull(linkId).stream()
				.filter(signal -> excludeId == null || !signal.getId().equals(excludeId))
				.filter(signal -> signal.getRhythm().equals(rhythm))
				.findFirst();
	}
}
