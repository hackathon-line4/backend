package com.sonulim;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 엔티티가 아직 없어 JPA/DataSource를 제외한 채로 컨텍스트 로딩만 검증한다.
// 첫 @Entity가 추가되면 이 제외를 지우고, CI가 이미 띄워주는 Postgres 서비스(ci.yml 참고)를
// 로컬에서는 docker-compose의 Postgres(또는 Testcontainers)로 맞춰 전체 부팅을 검증하도록 바꾼다.
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
class SonulimApplicationTests {

	@Test
	void contextLoads() {
	}
}
