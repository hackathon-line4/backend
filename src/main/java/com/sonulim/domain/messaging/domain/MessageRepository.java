package com.sonulim.domain.messaging.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

	/** ack 판정 시간이 지났는데 아직 ack가 없는 기록. */
	List<Message> findByDeliveryStatusAndAckDeadlineLessThanEqual(DeliveryStatus status, LocalDateTime at);

	/** 응답 대기가 끝났는데 아직 터치가 없는 기록. */
	List<Message> findByResponseStatusAndWaitUntilLessThanEqual(ResponseStatus status, LocalDateTime at);

	/**
	 * 당사자가 지금 울려야 하는 신호. 응답을 기다리는 중이거나 아직 ack하지 않은 것을 오래된 순으로 준다
	 * (받은 신호 조회 API). 연습 신호는 응답 상태가 없으므로 ack 전까지만 여기에 걸린다.
	 */
	@Query("select m from Message m where m.recipientId = :recipientId "
			+ "and (m.responseStatus = com.sonulim.domain.messaging.domain.ResponseStatus.WAITING "
			+ "or m.deliveryStatus in (com.sonulim.domain.messaging.domain.DeliveryStatus.REQUESTED, "
			+ "com.sonulim.domain.messaging.domain.DeliveryStatus.UNCONFIRMED)) "
			+ "order by m.requestedAt asc")
	List<Message> findIncoming(@Param("recipientId") UUID recipientId);

	/** 기록 상세는 타임라인을 함께 쓰므로 한 번에 가져온다. */
	@Query("select distinct m from Message m left join fetch m.timeline where m.id = :id")
	Optional<Message> findWithTimeline(@Param("id") UUID id);

	/** 전달된 지 일정 시간이 지나도록 응답이 없는 긴급 신호 중 아직 경고하지 않은 것. */
	List<Message> findByEmergencyTrueAndEmergencyWarningFalseAndResponseStatusAndDeliveredAtLessThanEqual(
			ResponseStatus status, LocalDateTime at);
}
