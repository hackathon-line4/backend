package com.sonulim.domain.link.domain;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LinkRepository extends JpaRepository<Link, UUID> {

	boolean existsByActiveGuardianId(UUID guardianId);

	boolean existsByActiveRecipientId(UUID recipientId);

	// 해제가 동시에 와도 한 번만 상태를 바꾼다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from Link l where l.id = :id")
	Optional<Link> findByIdForUpdate(UUID id);
}
