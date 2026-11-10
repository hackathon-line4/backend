package com.sonulim.domain.link.domain;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ConnectionCheckRepository extends JpaRepository<ConnectionCheck, UUID> {

	// 연결 행을 잠근 뒤 부른다 — 한 연결에 PENDING 확인은 많아야 하나다.
	Optional<ConnectionCheck> findFirstByLinkIdAndStatusOrderByRequestedAtDesc(UUID linkId,
			ConnectionCheckStatus status);

	// ack가 여러 번 동시에 와도 처음 한 번만 기록한다. 잠그는 순서는 항상 연결 → 확인이다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from ConnectionCheck c where c.id = :id")
	Optional<ConnectionCheck> findByIdForUpdate(UUID id);
}
