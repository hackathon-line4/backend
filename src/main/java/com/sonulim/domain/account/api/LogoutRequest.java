package com.sonulim.domain.account.api;

import jakarta.validation.constraints.NotBlank;

// installationId는 기기·푸시 토큰 등록이 구현되면 그 기기 등록을 지우는 데 쓴다.
public record LogoutRequest(@NotBlank String refreshToken, @NotBlank String installationId) {
}
