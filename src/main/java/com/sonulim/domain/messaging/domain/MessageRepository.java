package com.sonulim.domain.messaging.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {

	/** ack 판정 시간이 지났는데 아직 ack가 없는 기록. */
	List<Message> findByDeliveryStatusAndAckDeadlineLessThanEqual(DeliveryStatus status, LocalDateTime at);

	/** 응답 대기가 끝났는데 아직 터치가 없는 기록. */
	List<Message> findByResponseStatusAndWaitUntilLessThanEqual(ResponseStatus status, LocalDateTime at);

	/** 전달된 지 일정 시간이 지나도록 응답이 없는 긴급 신호 중 아직 경고하지 않은 것. */
	List<Message> findByEmergencyTrueAndEmergencyWarningFalseAndResponseStatusAndDeliveredAtLessThanEqual(
			ResponseStatus status, LocalDateTime at);
}
