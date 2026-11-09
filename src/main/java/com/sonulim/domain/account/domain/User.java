package com.sonulim.domain.account.domain;

import java.time.LocalDateTime;
import java.util.Locale;
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
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

// PostgreSQL에서 user는 예약어라 테이블 이름을 users로 둔다.
@Entity
@Table(name = "users")
public class User extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true, length = 320)
	private String email;

	@Column(nullable = false, length = 100)
	private String passwordHash;

	@Column(nullable = false, length = 20)
	private String name;

	// 가입 직후에는 역할이 없다(A-3 역할 선택에서 정함).
	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private Role role;

	private LocalDateTime roleSetAt;

	protected User() {
	}

	private User(String email, String passwordHash, String name) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.name = name;
	}

	public static User register(String email, String passwordHash, String name) {
		return new User(normalizeEmail(email), passwordHash, name.trim());
	}

	/**
	 * 이메일은 대소문자를 구분하지 않는다 — 저장·조회 모두 이 값으로 한다.
	 */
	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * MVP에서는 역할을 바꿀 수 없다. 같은 역할을 다시 고르면 그대로 두고(재시도 안전), 다른 역할이면 거부한다.
	 */
	public void selectRole(Role role, LocalDateTime now) {
		if (this.role == role) {
			return;
		}
		if (this.role != null) {
			throw new SonulimException(ErrorCode.ROLE_ALREADY_SET, "이미 역할을 선택했어요");
		}
		this.role = role;
		this.roleSetAt = now;
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getName() {
		return name;
	}

	public Role getRole() {
		return role;
	}

	public LocalDateTime getRoleSetAt() {
		return roleSetAt;
	}
}
