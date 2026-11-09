package com.sonulim.domain.messaging.api;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

/**
 * @param touchedAt 터치한 기기 시각. 참고용이고 응답 시각은 서버 시각으로 기록한다
 */
public record TouchRequest(@NotNull(message = "터치 시각이 필요해요") LocalDateTime touchedAt) {
}
