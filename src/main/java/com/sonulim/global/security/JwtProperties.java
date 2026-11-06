package com.sonulim.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * sonulim.jwt.* 설정. secret은 HS256 기준 32바이트 이상이어야 한다(짧으면 JwtTokenProvider 생성 시 실패).
 */
@ConfigurationProperties(prefix = "sonulim.jwt")
public record JwtProperties(String secret, long accessExpirationSeconds, long refreshExpirationSeconds) {
}
