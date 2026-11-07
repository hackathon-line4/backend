package com.sonulim.domain.link.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
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
import jakarta.persistence.UniqueConstraint;

import com.sonulim.global.BaseEntity;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

/**
 * 당사자가 연결 코드로 보낸 연결 요청(L-2). 보호자가 승인·거절하거나(L-3), 당사자가 취소하거나, 10분이 지나면 끝난다(Notion API 명세).
 *
 * 만료는 시각 기준이다. 서버 타이머가 아직 없으므로 요청을 읽거나 바꿀 때 expireIfDue로 판정한다.
 */
@Entity
@Table(name = "link_requests",
		uniqueConstraints = @UniqueConstraint(name = "uk_link_requests_recipient_idempotency_key",
				columnNames = {"recipientId", "idempotencyKey"}),
		indexes = {
				@Index(name = "idx_link_requests_guardian_id", columnList = "guardianId"),
				@Index(name = "idx_link_requests_recipient_id", columnList = "recipientId")
		})
public class LinkRequest extends BaseEntity {

	public static final Duration VALIDITY = Duration.ofMinutes(10);

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID guardianId;

	@Column(nullable = false)
	private UUID recipientId;

	@Column(nullable = false)
	private UUID linkCodeId;

	// 보호자 요청 화면의 '사용된 코드'. 코드가 바뀌어도 이 요청에 쓴 값을 그대로 보여준다.
	@Column(nullable = false, length = 6)
	private String code;

	@Column(nullable = false)
	private UUID idempotencyKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LinkRequestStatus status;

	@Column(nullable = false)
	private LocalDateTime requestedAt;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	// 승인·거절·취소한 시각. 만료는 사람이 정한 것이 아니라 기록하지 않는다(docs/domains/link.md Pending Decisions).
	private LocalDateTime decidedAt;

	// 승인으로 만들어진 연결.
	private UUID linkId;

	protected LinkRequest() {
	}

	private LinkRequest(UUID guardianId, UUID recipientId, LinkCode linkCode, UUID idempotencyKey,
			LocalDateTime now) {
		this.guardianId = guardianId;
		this.recipientId = recipientId;
		this.linkCodeId = linkCode.getId();
		this.code = linkCode.getCode();
		this.idempotencyKey = idempotencyKey;
		this.status = LinkRequestStatus.PENDING;
		this.requestedAt = now;
		this.expiresAt = now.plus(VALIDITY);
	}

	public static LinkRequest request(UUID recipientId, LinkCode linkCode, UUID idempotencyKey, LocalDateTime now) {
		return new LinkRequest(linkCode.getGuardianId(), recipientId, linkCode, idempotencyKey, now);
	}

	/**
	 * 대기 중인데 만료 시각이 지났으면 EXPIRED로 바꾼다. 바꿨으면 true.
	 */
	public boolean expireIfDue(LocalDateTime now) {
		if (status == LinkRequestStatus.PENDING && !expiresAt.isAfter(now)) {
			status = LinkRequestStatus.EXPIRED;
			return true;
		}
		return false;
	}

	/**
	 * 대기 중인지 확인한다(만료 판정 포함). 아니면 LINK_REQUEST_NOT_PENDING이고 details.status에 현재 상태를 담는다.
	 */
	public void requirePending(LocalDateTime now) {
		expireIfDue(now);
		if (status == LinkRequestStatus.PENDING) {
			return;
		}
		String message = status == LinkRequestStatus.EXPIRED ? "요청이 만료됐어요" : "이미 처리된 요청이에요";
		throw new SonulimException(ErrorCode.LINK_REQUEST_NOT_PENDING, message, null,
				Map.of("status", status.name()));
	}

	public void approve(UUID linkId, LocalDateTime now) {
		decide(LinkRequestStatus.APPROVED, now);
		this.linkId = linkId;
	}

	public void reject(LocalDateTime now) {
		decide(LinkRequestStatus.REJECTED, now);
	}

	public void cancel(LocalDateTime now) {
		decide(LinkRequestStatus.CANCELED, now);
	}

	public boolean involves(UUID userId) {
		return guardianId.equals(userId) || recipientId.equals(userId);
	}

	private void decide(LinkRequestStatus decided, LocalDateTime now) {
		requirePending(now);
		this.status = decided;
		this.decidedAt = now;
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

	public UUID getLinkCodeId() {
		return linkCodeId;
	}

	public String getCode() {
		return code;
	}

	public UUID getIdempotencyKey() {
		return idempotencyKey;
	}

	public LinkRequestStatus getStatus() {
		return status;
	}

	public LocalDateTime getRequestedAt() {
		return requestedAt;
	}

	public LocalDateTime getExpiresAt() {
		return expiresAt;
	}

	public LocalDateTime getDecidedAt() {
		return decidedAt;
	}

	public UUID getLinkId() {
		return linkId;
	}
}
