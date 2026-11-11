package com.sonulim.domain.location.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param requestTimeout 위치 새로고침 요청을 이만큼 기다린다. 백엔드 제안값이고 프론트 확인 전이다
 *                       (docs/domains/location.md의 Pending Decisions)
 */
@ConfigurationProperties(prefix = "sonulim.location")
public record LocationProperties(Duration requestTimeout) {
}
