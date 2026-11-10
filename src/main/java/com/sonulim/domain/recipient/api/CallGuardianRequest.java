package com.sonulim.domain.recipient.api;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

import com.sonulim.domain.recipient.domain.RecipientSignalType;

/**
 * @param sentAt 기기에서 보낸 시각. 참고용이고 기록에는 서버 시각을 쓴다(보호자 부르기 API)
 */
public record CallGuardianRequest(
		@NotNull(message = "신호 종류가 필요해요") RecipientSignalType type,
		@NotNull(message = "보낸 시각이 필요해요") LocalDateTime sentAt) {
}
