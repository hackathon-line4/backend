package com.sonulim.domain.recipient.api;

import jakarta.validation.constraints.NotNull;

public record ReceiptRequest(@NotNull(message = "보고 종류가 필요해요") ReceiptType type) {
}
