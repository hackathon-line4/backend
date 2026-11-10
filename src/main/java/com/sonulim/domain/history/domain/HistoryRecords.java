package com.sonulim.domain.history.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 기록 읽기. 구현(history.infrastructure)만 messaging·recipient의 엔티티를 읽는다 — 그 도메인이 조회 서비스를 내놓으면
 * 구현만 바꾼다. 시각 범위는 UTC(서버 저장 기준)다.
 */
public interface HistoryRecords {

	/**
	 * 이 연결에서 이 보호자의 기록 중 [from, to) 안에 생긴 것. 순서는 정하지 않는다.
	 */
	List<HistoryItem> findByGuardian(UUID linkId, UUID guardianId, LocalDateTime from, LocalDateTime to);

	/**
	 * 이 연결에서 이 보호자의 기록이 before 전에 하나라도 있는지.
	 */
	boolean existsBefore(UUID linkId, UUID guardianId, LocalDateTime before);
}
