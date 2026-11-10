package com.sonulim.domain.messaging.api;

import java.util.List;

public record IncomingMessagesResponse(List<IncomingMessageResponse> items) {
}
