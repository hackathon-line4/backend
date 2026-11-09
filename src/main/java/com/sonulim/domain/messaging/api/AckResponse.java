package com.sonulim.domain.messaging.api;

import java.time.LocalDateTime;

import com.sonulim.domain.messaging.domain.Message;

/**
 * @param waitUntil 이 시각까지 반복 진동을 유지한다. 연습 신호는 응답을 받지 않으므로 null
 */
public record AckResponse(LocalDateTime deliveredAt, LocalDateTime waitUntil) {

	static AckResponse from(Message message) {
		return new AckResponse(message.getDeliveredAt(), message.getWaitUntil());
	}
}
