package com.sonulim.domain.account.application;

import java.util.UUID;

import com.sonulim.domain.account.domain.Role;

/**
 * 역할 선택 직후 발행. 역할별 기본 설정은 각 설정 도메인이 이 이벤트를 받아 만든다(같은 트랜잭션).
 * 같은 역할을 다시 고른 경우에도 발행되므로 받는 쪽은 이미 있으면 건너뛴다.
 */
public record RoleSelectedEvent(UUID userId, Role role) {
}
