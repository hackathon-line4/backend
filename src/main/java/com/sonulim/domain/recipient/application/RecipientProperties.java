package com.sonulim.domain.recipient.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기획과 아직 확정하지 못한 값(docs/domains/recipient.md의 Pending Decisions).
 *
 * @param renotifyInterval 도움 요청을 다시 알리는 간격(백엔드 제안 1분)
 * @param renotifyLimit    최대 재알림 횟수(백엔드 제안 5회). 보호자가 열어 보면 그 전에 멈춘다
 */
@ConfigurationProperties(prefix = "sonulim.recipient")
public record RecipientProperties(Duration renotifyInterval, int renotifyLimit) {
}
