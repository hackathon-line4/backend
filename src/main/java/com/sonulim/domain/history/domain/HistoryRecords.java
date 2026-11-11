package com.sonulim.domain.history.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

	/**
	 * 이 연결에서 이 보호자의 기록 중 가장 최근 limit건(종류 무관, 최신순). 홈 요약(link, #81)용.
	 */
	List<HistoryItem> findLatest(UUID linkId, UUID guardianId, int limit);

	/**
	 * 보호자가 아직 열어 보지 않은 도움 요청 중 가장 최근 것.
	 */
	Optional<HistoryItem> findLatestUnopenedHelpRequest(UUID linkId, UUID guardianId);

	/**
	 * 가장 최근 보호자 신호(연습 제외).
	 */
	Optional<HistoryItem> findLatestGuardianSignal(UUID linkId, UUID guardianId);

	/**
	 * 터치 응답을 받은 보호자 신호 중 응답 시각이 가장 최근인 것.
	 */
	Optional<HistoryItem> findLatestRespondedSignal(UUID linkId, UUID guardianId);
}
