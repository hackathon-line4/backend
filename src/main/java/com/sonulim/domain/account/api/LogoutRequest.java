package com.sonulim.domain.account.api;

import jakarta.validation.constraints.NotBlank;

// installationId는 로그아웃하는 기기의 푸시 등록을 지우는 데 쓴다.
public record LogoutRequest(@NotBlank String refreshToken, @NotBlank String installationId) {
}
