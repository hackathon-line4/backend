package com.sonulim.global.idempotency;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보관 기간이 지난 중복 방지 기록을 지운다. 지우지 않으면 테이블이 계속 커지고,
 * 보관 기간을 넘긴 키는 다시 쓸 수 있어야 한다(공통 규칙: 24시간).
 *
 * <p>ponytail: 주기적으로 지우는 방식이라 기한 직후 잠시 남아 있을 수 있다. 재사용이 드물어 문제되지 않는다.
 */
@Component
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyCleanupScheduler {

	private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupScheduler.class);

	private final IdempotencyRecordRepository repository;
	private final IdempotencyProperties properties;
	private final Clock clock;

	public IdempotencyCleanupScheduler(IdempotencyRecordRepository repository, IdempotencyProperties properties,
			Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${sonulim.idempotency.cleanup-interval-ms}")
	@Transactional
	public void deleteExpired() {
		LocalDateTime cutoff = LocalDateTime.now(clock).minus(properties.retention());
		int deleted = repository.deleteOlderThan(cutoff);
		if (deleted > 0) {
			log.debug("보관 기간이 지난 중복 방지 기록 {}건 삭제", deleted);
		}
	}
}
