package com.sonulim.domain.account.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.sonulim.global.BaseEntity;

/**
 * 갱신 토큰. 원문은 저장하지 않고 SHA-256 해시만 저장한다(DB가 유출돼도 토큰으로 쓸 수 없게).
 * 회전 방식이라 한 번 쓰면 무효화하고 새로 발급한 토큰을 replacedById에 남긴다.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID userId;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	private LocalDateTime revokedAt;

	private UUID replacedById;

	protected RefreshToken() {
	}

	private RefreshToken(UUID userId, String tokenHash, LocalDateTime expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public static RefreshToken issue(UUID userId, String tokenHash, LocalDateTime expiresAt) {
		return new RefreshToken(userId, tokenHash, expiresAt);
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public boolean isExpired(LocalDateTime now) {
		return !expiresAt.isAfter(now);
	}

	public void rotateTo(UUID nextTokenId, LocalDateTime now) {
		this.revokedAt = now;
		this.replacedById = nextTokenId;
	}

	public void revoke(LocalDateTime now) {
		if (revokedAt == null) {
			this.revokedAt = now;
		}
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public LocalDateTime getExpiresAt() {
		return expiresAt;
	}

	public LocalDateTime getRevokedAt() {
		return revokedAt;
	}

	public UUID getReplacedById() {
		return replacedById;
	}
}
