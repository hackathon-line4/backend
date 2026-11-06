package com.sonulim;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 실제 DB(로컬: docker-compose Postgres, CI: ci.yml의 Postgres 서비스)로 전체 부팅을 검증한다.
@SpringBootTest
class SonulimApplicationTests {

	@Test
	void contextLoads() {
	}
}
