package com.sonulim.domain.link.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import com.sonulim.global.BaseEntity;

/**
 * 보호자-당사자 연결. 연결 요청 승인(L-3)으로 만들어진다.
 *
 * MVP는 보호자 1명 : 당사자 1명이다. activeGuardianId·activeRecipientId는 살아 있는 연결에만 값이 있는 유니크 컬럼이라,
 * 승인이 동시에 들어와도 DB가 한 사람당 살아 있는 연결 하나만 허용한다(PostgreSQL 유니크 제약은 null을 여러 개 허용).
 * 연결 해제(L-4)에서 두 컬럼을 비운다. 1:N으로 바뀌면 activeGuardianId만 없애면 된다(docs/domains/link.md Pending Decisions).
 */
@Entity
@Table(name = "links", indexes = {
		@Index(name = "idx_links_guardian_id", columnList = "guardianId"),
		@Index(name = "idx_links_recipient_id", columnList = "recipientId")
})
public class Link extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID guardianId;

	@Column(nullable = false)
	private UUID recipientId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LinkStatus status;

	@Column(nullable = false)
	private LocalDateTime linkedAt;

	@Column(unique = true)
	private UUID activeGuardianId;

	@Column(unique = true)
	private UUID activeRecipientId;

	protected Link() {
	}

	private Link(UUID guardianId, UUID recipientId, LocalDateTime now) {
		this.guardianId = guardianId;
		this.recipientId = recipientId;
		this.status = LinkStatus.ACTIVE;
		this.linkedAt = now;
		this.activeGuardianId = guardianId;
		this.activeRecipientId = recipientId;
	}

	public static Link connect(UUID guardianId, UUID recipientId, LocalDateTime now) {
		return new Link(guardianId, recipientId, now);
	}

	public UUID getId() {
		return id;
	}

	public UUID getGuardianId() {
		return guardianId;
	}

	public UUID getRecipientId() {
		return recipientId;
	}

	public LinkStatus getStatus() {
		return status;
	}

	public LocalDateTime getLinkedAt() {
		return linkedAt;
	}
}
