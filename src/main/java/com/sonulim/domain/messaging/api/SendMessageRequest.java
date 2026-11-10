package com.sonulim.domain.messaging.api;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record SendMessageRequest(@NotNull(message = "보낼 신호를 골라 주세요") UUID signalId) {
}
