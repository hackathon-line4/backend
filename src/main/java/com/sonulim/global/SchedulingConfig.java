package com.sonulim.global;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 서버 타이머(응답 없음 판정 등)를 돌리기 위한 스케줄링 활성화.
 * @SpringBootApplication이 아닌 별도 클래스에 두어 슬라이스 테스트에는 켜지지 않게 한다(JpaAuditingConfig와 같은 이유).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
