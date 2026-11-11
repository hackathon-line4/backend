package com.sonulim.domain.account.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkRequest;
import com.sonulim.domain.link.domain.LinkRequestStatus;
import com.sonulim.domain.link.domain.LinkStatus;

/**
 * GET /me에 내려줄 내 연결·대기 중 연결 요청을 읽는다(읽기 전용).
 *
 * <p>link 도메인에 "사용자 기준 조회"가 아직 없어 link 코드를 고치지 않고 엔티티를 직접 조회한다.
 * link에 같은 조회가 생기면 그쪽 서비스로 바꾼다(docs/domains/account.md 후속 작업).
 */
@Component
class MyLinksReader {

	private final EntityManager entityManager;
	private final Clock clock;

	MyLinksReader(EntityManager entityManager, Clock clock) {
		this.entityManager = entityManager;
		this.clock = clock;
	}

	/**
	 * 내가 보호자나 당사자로 속한 ACTIVE 연결. MVP는 1:1이라 0~1개다.
	 */
	List<Link> activeLinks(UUID userId) {
		return entityManager.createQuery(
						"select l from Link l where l.status = :status "
								+ "and (l.guardianId = :userId or l.recipientId = :userId) order by l.linkedAt desc",
						Link.class)
				.setParameter("status", LinkStatus.ACTIVE)
				.setParameter("userId", userId)
				.getResultList();
	}

	/**
	 * 당사자가 보낸 대기 중 요청 중 가장 최근 것. 만료 처리는 link가 조회·변경 시점에 하므로 여기서는 만료 시각으로 걸러낸다.
	 */
	Optional<LinkRequest> pendingRequestOfRecipient(UUID recipientId) {
		return entityManager.createQuery(
						"select r from LinkRequest r where r.recipientId = :recipientId and r.status = :status "
								+ "and r.expiresAt > :now order by r.requestedAt desc",
						LinkRequest.class)
				.setParameter("recipientId", recipientId)
				.setParameter("status", LinkRequestStatus.PENDING)
				.setParameter("now", LocalDateTime.now(clock))
				.setMaxResults(1)
				.getResultStream()
				.findFirst();
	}
}
