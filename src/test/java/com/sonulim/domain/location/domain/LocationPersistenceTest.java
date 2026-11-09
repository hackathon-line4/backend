package com.sonulim.domain.location.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class LocationPersistenceTest {

	@Autowired
	private EntityManager entityManager;

	@Test
	void onlyOneLastLocationIsKeptPerRecipient() {
		UUID recipientId = UUID.randomUUID();
		LastLocation location = LastLocation.of(recipientId, new BigDecimal("37.566500"),
				new BigDecimal("126.978000"), 25, LocalDateTime.now());
		entityManager.persist(location);
		entityManager.flush();
		entityManager.clear();

		// 같은 당사자의 새 위치는 행을 늘리지 않고 덮어쓴다 — 당사자 ID가 기본 키다
		LastLocation reloaded = entityManager.find(LastLocation.class, recipientId);
		reloaded.overwriteWith(new BigDecimal("35.179554"), new BigDecimal("129.075641"), 18, LocalDateTime.now());
		entityManager.flush();
		entityManager.clear();

		assertThat(entityManager.find(LastLocation.class, recipientId).getLongitude())
				.isEqualByComparingTo("129.075641");
		assertThat(entityManager.createQuery(
				"select count(l) from LastLocation l where l.recipientId = :id", Long.class)
				.setParameter("id", recipientId).getSingleResult()).isEqualTo(1L);
	}

	@Test
	void locationRequestRoundTrips() {
		LocationRequest request = LocationRequest.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				LocalDateTime.now(), Duration.ofSeconds(30));
		request.markFailed(LocationFailureReason.SHARING_OFF);

		entityManager.persist(request);
		entityManager.flush();
		entityManager.clear();

		LocationRequest reloaded = entityManager.find(LocationRequest.class, request.getId());
		assertThat(reloaded.getStatus()).isEqualTo(LocationRequestStatus.FAILED);
		assertThat(reloaded.getFailureReason()).isEqualTo(LocationFailureReason.SHARING_OFF);
		assertThat(reloaded.getTimeoutAt()).isNotNull();
	}
}
