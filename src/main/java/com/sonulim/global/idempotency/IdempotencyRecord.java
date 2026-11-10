package com.sonulim.global.idempotency;

import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.sonulim.global.BaseEntity;

/**
 * 같은 Idempotency-Key로 다시 들어온 요청에 처음 응답을 그대로 돌려주기 위한 기록(공통 규칙).
 *
 * <p>먼저 자리를 잡고(응답 없이 저장) 실제 작업을 한 뒤 응답을 채운다. 같은 키가 동시에 들어오면
 * (user_id, key) 유니크 제약에 걸려 한 쪽만 작업을 수행한다 — 신호가 두 번 울리면 안 되기 때문이다.
 */
@Entity
@Table(name = "idempotency_keys",
		uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_user_key", columnNames = { "user_id", "key_value" }))
public class IdempotencyRecord extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	// key는 여러 DB에서 예약어라 컬럼명을 따로 둔다.
	@Column(name = "key_value", nullable = false)
	private UUID key;

	/** 같은 키를 다른 요청에 재사용했는지 가리는 값. 본문이 다르면 422로 거절한다. */
	@Column(nullable = false, length = 64)
	private String requestHash;

	/** 처음 응답. 작업이 끝나야 채워지므로, 비어 있으면 아직 처리 중이라는 뜻이다. */
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> response;

	protected IdempotencyRecord() {
	}

	private IdempotencyRecord(UUID userId, UUID key, String requestHash) {
		this.userId = userId;
		this.key = key;
		this.requestHash = requestHash;
	}

	/** 작업을 시작하기 전에 자리를 잡는다. */
	public static IdempotencyRecord claim(UUID userId, UUID key, String requestHash) {
		return new IdempotencyRecord(userId, key, requestHash);
	}

	public void completeWith(Map<String, Object> response) {
		this.response = response;
	}

	public boolean isInFlight() {
		return response == null;
	}

	public boolean matches(String requestHash) {
		return this.requestHash.equals(requestHash);
	}

	public UUID getId() {
		return id;
	}

	public Map<String, Object> getResponse() {
		return response;
	}
}
