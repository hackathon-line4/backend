package com.sonulim.domain.signal.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalRepository extends JpaRepository<Signal, UUID> {

	List<Signal> findByLinkIdAndDeletedAtIsNull(UUID linkId);

	Optional<Signal> findByIdAndDeletedAtIsNull(UUID id);

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
