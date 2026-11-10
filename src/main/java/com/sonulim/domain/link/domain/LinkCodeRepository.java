package com.sonulim.domain.link.domain;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LinkCodeRepository extends JpaRepository<LinkCode, UUID> {

	Optional<LinkCode> findByGuardianIdAndIdempotencyKey(UUID guardianId, UUID idempotencyKey);

	@Query("select count(c) > 0 from LinkCode c where c.code = :code and c.revokedAt is null and c.expiresAt > :now")
	boolean existsLiveByCode(String code, LocalDateTime now);

	@Query("select c from LinkCode c where c.guardianId = :guardianId and c.revokedAt is null and c.expiresAt > :now "
			+ "order by c.issuedAt desc limit 1")
	Optional<LinkCode> findLatestLiveByGuardianId(UUID guardianId, LocalDateTime now);

	/**
	 * 같은 값의 코드 중 무효화되지 않은 가장 최근 코드. 살아 있는 코드는 값이 겹치지 않으므로, 맨 앞이 살아 있지 않으면
	 * 이 값으로 살아 있는 코드는 없다(만료됐거나 만들어진 적 없음).
	 */
	@Query("select c from LinkCode c where c.code = :code and c.revokedAt is null order by c.issuedAt desc limit 1")
	Optional<LinkCode> findLatestNotRevokedByCode(String code);

	// 이미 만료된 코드는 그대로 둔다 — 확인할 때 '만료됨'(LINK_CODE_EXPIRED)으로 구분해 응답해야 하기 때문이다.
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update LinkCode c set c.revokedAt = :now "
			+ "where c.guardianId = :guardianId and c.revokedAt is null and c.expiresAt > :now")
	int revokeLiveByGuardianId(UUID guardianId, LocalDateTime now);
}
