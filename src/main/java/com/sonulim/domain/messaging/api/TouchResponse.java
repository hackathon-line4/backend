package com.sonulim.domain.messaging.api;

import java.time.LocalDateTime;

import com.sonulim.domain.messaging.domain.Message;
import com.sonulim.domain.messaging.domain.ResponseStatus;

/**
 * @param late 응답 없음 판정 뒤에 들어온 터치인지
 */
public record TouchResponse(ResponseStatus status, LocalDateTime respondedAt, boolean late) {

	static TouchResponse from(Message message) {
		return new TouchResponse(message.getResponseStatus(), message.getRespondedAt(), message.isLate());
	}
}
