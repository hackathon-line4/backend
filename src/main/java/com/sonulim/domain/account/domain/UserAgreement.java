package com.sonulim.domain.account.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.sonulim.global.BaseEntity;

@Entity
@Table(name = "user_agreements")
public class UserAgreement extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AgreementType type;

	// 약관 동의 시각(UTC). 기능명세서 A-1의 "동의 여부와 동의 시각".
	@Column(nullable = false)
	private LocalDateTime agreedAt;

	protected UserAgreement() {
	}

	private UserAgreement(UUID userId, AgreementType type, LocalDateTime agreedAt) {
		this.userId = userId;
		this.type = type;
		this.agreedAt = agreedAt;
	}

	public static UserAgreement agree(UUID userId, AgreementType type, LocalDateTime agreedAt) {
		return new UserAgreement(userId, type, agreedAt);
	}

	public UUID getUserId() {
		return userId;
	}

	public AgreementType getType() {
		return type;
	}

	public LocalDateTime getAgreedAt() {
		return agreedAt;
	}
}
