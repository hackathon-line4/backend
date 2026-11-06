package com.sonulim.domain.messaging.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기획·프론트와 아직 확정하지 못한 값들(docs/domains/messaging.md의 Pending Decisions).
 * 코드에 상수로 박지 않고 설정으로 빼 둬서, 확정되면 yml만 고치면 되게 한다.
 *
 * @param emergencyWarningDelay 긴급 신호가 전달 후 이만큼 응답이 없으면 보호자에게 경고(백엔드 제안 30초)
 */
@ConfigurationProperties(prefix = "sonulim.messaging")
public record MessagingProperties(Duration emergencyWarningDelay) {
}
