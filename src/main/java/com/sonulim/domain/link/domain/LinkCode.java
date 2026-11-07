package com.sonulim.domain.link.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.sonulim.global.BaseEntity;

/**
 * 보호자가 만든 연결 코드(L-1). 6자리 숫자이고 만든 뒤 10분 동안 유효하다(Notion API 명세).
 *
 * 살아 있는 코드 = 무효화되지 않았고 아직 만료되지 않은 코드. 만료는 시간 기준이라 DB 유니크 제약으로 걸 수 없으므로
 * 살아 있는 코드끼리 겹치지 않게 하는 일은 만들 때 확인한다(LinkCodeService).
 */
@Entity
@Table(name = "link_codes",
		uniqueConstraints = @UniqueConstraint(name = "uk_link_codes_guardian_idempotency_key",
				columnNames = {"guardianId", "idempotencyKey"}),
		indexes = {
				@Index(name = "idx_link_codes_code", columnList = "code"),
				@Index(name = "idx_link_codes_guardian_id", columnList = "guardianId")
		})
public class LinkCode extends BaseEntity {

	public static final Duration VALIDITY = Duration.ofMinutes(10);

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID guardianId;

	@Column(nullable = false, length = 6)
	private String code;

	// 같은 키로 다시 요청하면 새로 만들지 않고 이 코드를 그대로 돌려준다.
	@Column(nullable = false)
	private UUID idempotencyKey;

	// 응답의 createdAt. 감사 필드(BaseEntity.createdAt)와 달리 Clock으로 찍어 expiresAt과 정확히 10분 차이가 나게 한다.
	@Column(nullable = false)
	private LocalDateTime issuedAt;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	// 같은 보호자가 새 코드를 만들면 기록한다.
	private LocalDateTime revokedAt;

	// 이 코드로 온 연결 요청이 승인된 시각. 거절된 요청은 코드를 쓰지 않는다(만료 전이면 다시 쓸 수 있음).
	private LocalDateTime usedAt;

	protected LinkCode() {
	}

	private LinkCode(UUID guardianId, String code, UUID idempotencyKey, LocalDateTime now) {
		this.guardianId = guardianId;
		this.code = code;
		this.idempotencyKey = idempotencyKey;
		this.issuedAt = now;
		this.expiresAt = now.plus(VALIDITY);
	}

	public static LinkCode issue(UUID guardianId, String code, UUID idempotencyKey, LocalDateTime now) {
		return new LinkCode(guardianId, code, idempotencyKey, now);
	}

	public boolean isExpired(LocalDateTime now) {
		return !expiresAt.isAfter(now);
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public boolean isUsed() {
		return usedAt != null;
	}

	public void markUsed(LocalDateTime now) {
		if (usedAt == null) {
			this.usedAt = now;
		}
	}

	public boolean isLive(LocalDateTime now) {
		return !isRevoked() && !isExpired(now);
	}

	public UUID getId() {
		return id;
	}

	public UUID getGuardianId() {
		return guardianId;
	}

	public String getCode() {
		return code;
	}

	public UUID getIdempotencyKey() {
		return idempotencyKey;
	}

	public LocalDateTime getIssuedAt() {
		return issuedAt;
	}

	public LocalDateTime getExpiresAt() {
		return expiresAt;
	}

	public LocalDateTime getRevokedAt() {
		return revokedAt;
	}

	public LocalDateTime getUsedAt() {
		return usedAt;
	}
}
