package com.sonulim.domain.link.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LinkRequestRepository extends JpaRepository<LinkRequest, UUID> {

	Optional<LinkRequest> findByRecipientIdAndIdempotencyKey(UUID recipientId, UUID idempotencyKey);

	// 승인·거절·취소가 동시에 오면 하나만 상태를 바꾸고, 나머지는 바뀐 상태를 보고 LINK_REQUEST_NOT_PENDING이 되게 한다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from LinkRequest r where r.id = :id")
	Optional<LinkRequest> findByIdForUpdate(UUID id);

	List<LinkRequest> findByGuardianIdAndStatusOrderByRequestedAtDesc(UUID guardianId, LinkRequestStatus status);

	List<LinkRequest> findByGuardianIdAndRequestedAtGreaterThanEqualOrderByRequestedAtDesc(UUID guardianId,
			LocalDateTime since);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update LinkRequest r set r.status = com.sonulim.domain.link.domain.LinkRequestStatus.EXPIRED "
			+ "where r.guardianId = :guardianId "
			+ "and r.status = com.sonulim.domain.link.domain.LinkRequestStatus.PENDING and r.expiresAt <= :now")
	int expireDueByGuardianId(UUID guardianId, LocalDateTime now);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update LinkRequest r set r.status = com.sonulim.domain.link.domain.LinkRequestStatus.EXPIRED "
			+ "where r.recipientId = :recipientId "
			+ "and r.status = com.sonulim.domain.link.domain.LinkRequestStatus.PENDING and r.expiresAt <= :now")
	int expireDueByRecipientId(UUID recipientId, LocalDateTime now);

	// 만료된 요청은 먼저 expireDueByRecipientId로 정리한 뒤 부른다 — 남은 PENDING은 모두 아직 살아 있는 요청이다.
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update LinkRequest r set r.status = com.sonulim.domain.link.domain.LinkRequestStatus.CANCELED, "
			+ "r.decidedAt = :now where r.recipientId = :recipientId "
			+ "and r.status = com.sonulim.domain.link.domain.LinkRequestStatus.PENDING")
	int cancelPendingByRecipientId(UUID recipientId, LocalDateTime now);
}
