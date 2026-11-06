package com.sonulim;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

// Signal이 첫 실제 @Entity로 들어오고 global.JpaAuditingConfig(@EnableJpaAuditing)도 켜지면서
// 더 이상 "DB 없이 뜨는" 전제가 성립하지 않는다 — 실제 Postgres 대신 내장 H2로 전체 컨텍스트가
// 뜨는지 검증한다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class SonulimApplicationTests {

	@Test
	void contextLoads() {
	}
}
