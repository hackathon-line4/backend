package com.sonulim.domain.recipient.domain;

import java.math.BigDecimal;
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
class RecipientPersistenceTest {

	@Autowired
	private EntityManager entityManager;

	@Test
	void helpRequestKeepsItsLocationAndReceiptThroughTheDatabase() {
		HelpRequest request = HelpRequest.send(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				LocalDateTime.now(), LocationStatus.ATTACHED,
				new CapturedLocation(new BigDecimal("37.566500"), new BigDecimal("126.978000"), 25,
						LocalDateTime.now()));
		request.fillAddress("서울 중구 세종대로 110");
		LocalDateTime opened = LocalDateTime.now();
		request.markOpened(opened);

		entityManager.persist(request);
		entityManager.flush();
		entityManager.clear();

		HelpRequest reloaded = entityManager.find(HelpRequest.class, request.getId());
		// 소수점 6자리가 그대로 보존돼야 한다 — 지도에 찍히는 좌표다
		assertThat(reloaded.getLocation().getLatitude()).isEqualByComparingTo("37.566500");
		assertThat(reloaded.getLocation().getLongitude()).isEqualByComparingTo("126.978000");
		assertThat(reloaded.getLocation().getAddress()).isEqualTo("서울 중구 세종대로 110");
		assertThat(reloaded.getLocationStatus()).isEqualTo(LocationStatus.ATTACHED);
		assertThat(reloaded.getGuardianOpenedAt()).isNotNull();
		assertThat(reloaded.getGuardianDeliveredAt()).isNotNull();
	}

	@Test
	void aRequestWithoutLocationStoresNothingInTheLocationColumns() {
		HelpRequest request = HelpRequest.send(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				LocalDateTime.now(), LocationStatus.PENDING, null);

		entityManager.persist(request);
		entityManager.flush();
		entityManager.clear();

		assertThat(entityManager.find(HelpRequest.class, request.getId()).getLocation()).isNull();
	}

	@Test
	void anUntouchedRequestCanStillRecordDeliveryAfterReloading() {
		// Hibernate는 임베디드의 모든 컬럼이 null이면 필드를 null로 되돌린다.
		// 아직 아무도 받지 않은 기록을 다시 읽어 와도 도착 보고가 들어가야 한다.
		HelpRequest request = HelpRequest.send(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				LocalDateTime.now(), LocationStatus.PENDING, null);
		entityManager.persist(request);
		entityManager.flush();
		entityManager.clear();

		HelpRequest reloaded = entityManager.find(HelpRequest.class, request.getId());
		assertThat(reloaded.getGuardianDeliveredAt()).isNull();
		reloaded.markDelivered(LocalDateTime.now());
		entityManager.flush();
		entityManager.clear();

		assertThat(entityManager.find(HelpRequest.class, request.getId()).getGuardianDeliveredAt()).isNotNull();
	}

	@Test
	void anUnreadSignalCanStillRecordDeliveryAfterReloading() {
		RecipientSignal signal = RecipientSignal.send(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				RecipientSignalType.CALL_GUARDIAN, LocalDateTime.now());
		entityManager.persist(signal);
		entityManager.flush();
		entityManager.clear();

		RecipientSignal reloaded = entityManager.find(RecipientSignal.class, signal.getId());
		reloaded.markOpened(LocalDateTime.now());
		entityManager.flush();
		entityManager.clear();

		RecipientSignal opened = entityManager.find(RecipientSignal.class, signal.getId());
		assertThat(opened.getGuardianOpenedAt()).isNotNull();
		// 열람이 먼저 와도 도착이 같은 시각으로 채워진다
		assertThat(opened.getGuardianDeliveredAt()).isEqualTo(opened.getGuardianOpenedAt());
	}

	@Test
	void recipientSignalRoundTrips() {
		RecipientSignal signal = RecipientSignal.send(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				RecipientSignalType.CALL_GUARDIAN, LocalDateTime.now());
		entityManager.persist(signal);
		entityManager.flush();
		entityManager.clear();

		RecipientSignal reloaded = entityManager.find(RecipientSignal.class, signal.getId());
		assertThat(reloaded.getType()).isEqualTo(RecipientSignalType.CALL_GUARDIAN);
		assertThat(reloaded.getGuardianOpenedAt()).isNull();
	}
}
