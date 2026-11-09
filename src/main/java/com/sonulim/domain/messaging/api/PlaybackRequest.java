package com.sonulim.domain.messaging.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param count 지금까지 재생한 누적 횟수. 매 재생마다 보내도 되고 반복을 멈출 때 한 번만 보내도 된다
 */
public record PlaybackRequest(
		@NotNull(message = "재생 횟수가 필요해요") @Min(value = 1, message = "재생 횟수는 1 이상이어야 해요") Integer count) {
}
