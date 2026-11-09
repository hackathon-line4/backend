package com.sonulim.global.idempotency;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

	Optional<IdempotencyRecord> findByUserIdAndKey(UUID userId, UUID key);

	/** 보관 기간이 지난 기록을 지운다(공통 규칙: 24시간 보관). */
	@Modifying
	@Query("delete from IdempotencyRecord r where r.createdAt < :cutoff")
	int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
