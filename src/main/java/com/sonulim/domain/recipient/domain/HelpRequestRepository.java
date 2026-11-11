package com.sonulim.domain.recipient.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpRequestRepository extends JpaRepository<HelpRequest, UUID> {

	/** 아직 열어 보지 않았고 재알림 한도에 닿지 않은 요청. */
	List<HelpRequest> findByReceiptOpenedAtIsNullAndRenotifyCountLessThan(int limit);

	/**
	 * 이 요청 다음에 온 도움 요청. 상세의 '요청 이후 주고받은 신호'를 다음 요청 전까지만 보여 주기 위해 쓴다.
	 */
	Optional<HelpRequest> findFirstByLinkIdAndSentAtGreaterThanOrderBySentAtAsc(UUID linkId, LocalDateTime sentAt);
}
