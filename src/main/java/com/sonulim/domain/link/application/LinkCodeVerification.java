package com.sonulim.domain.link.application;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 코드 확인 결과: 코드를 만든 보호자와 코드 만료 시각.
 */
public record LinkCodeVerification(UUID guardianId, String guardianName, LocalDateTime expiresAt) {
}
