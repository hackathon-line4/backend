package com.sonulim.domain.messaging.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * @param minutes 늘릴 대기 시간. 생략하면 보호자 설정값을 쓴다(더 기다리기 API)
 */
public record ExtendWaitRequest(
		@Min(value = 5, message = "대기 시간은 5분 이상이어야 해요")
		@Max(value = 30, message = "대기 시간은 30분 이하여야 해요") Integer minutes) {
}
