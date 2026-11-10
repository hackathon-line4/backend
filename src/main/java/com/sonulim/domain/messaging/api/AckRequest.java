package com.sonulim.domain.messaging.api;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

/**
 * @param receivedAt 기기가 신호를 받은 시각. 참고용으로만 저장하고 전달 완료 판정에는 서버 시각을 쓴다
 */
public record AckRequest(@NotNull(message = "받은 시각이 필요해요") LocalDateTime receivedAt) {
}
