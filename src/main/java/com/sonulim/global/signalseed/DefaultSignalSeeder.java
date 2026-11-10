package com.sonulim.global.signalseed;

import java.util.UUID;

/**
 * 연결 승인(link 도메인) 시 이 연결에 기본 신호를 채워 넣기 위한 계약.
 * LinkAccessGuard와 대칭 — signal이 정의·구현하고, link는 이 인터페이스만 의존한다
 * (docs/architecture.md "도메인 간 의존 방향": 도메인끼리 직접 의존하지 않고 global을 통해 연동).
 */
public interface DefaultSignalSeeder {

	void seedDefaults(UUID linkId);
}
