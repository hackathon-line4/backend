package com.sonulim.global.idempotency;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param retention 기록을 보관하는 기간. 공통 규칙에 24시간으로 확정되어 있다
 */
@ConfigurationProperties(prefix = "sonulim.idempotency")
public record IdempotencyProperties(Duration retention) {
}
