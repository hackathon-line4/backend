package com.sonulim.global.security;

import java.util.UUID;

/**
 * 액세스 토큰에서 꺼낸 인증 사용자. 컨트롤러에서 @AuthenticationPrincipal로 받는다.
 * role은 역할 선택 전이면 null이다. global은 도메인에 의존하지 않으므로 account의 Role enum 대신 이름 문자열로 둔다.
 */
public record AuthenticatedUser(UUID userId, String role) {
}
