package com.sonulim.global;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseEntity의 createdAt/updatedAt 자동 기록. 첫 실제 @Entity(account.User)와 함께 켠다(docs/architecture.md).
 * @SpringBootApplication이 아닌 별도 클래스에 두어 @WebMvcTest 같은 슬라이스 테스트에는 적용되지 않게 한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
