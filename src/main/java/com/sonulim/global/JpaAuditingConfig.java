package com.sonulim.global;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseEntity의 @CreatedDate/@LastModifiedDate를 실제로 채워주는 설정.
 * Signal이 BaseEntity를 쓰는 첫 실제 도메인 엔티티라서 이제 켠다(docs/architecture.md 참고).
 * 누가 만들었는지(AuditorAware)는 지금 추적하지 않는다 — 생성·수정 시각만 관리한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
