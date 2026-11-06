package com.sonulim.domain.messaging.domain;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 기록 상세의 타임라인 한 줄. Message가 상태를 바꿀 때 직접 남긴다 — 서비스가 따로 기록하면 빠뜨리기 쉽다.
 */
@Entity
@Table(name = "message_events")
public class MessageEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MessageEventStep step;

	@Column(name = "occurred_at", nullable = false)
	private LocalDateTime at;

	/** 단계별 부가 값. 지금은 PLAYED의 count만 쓴다. */
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> meta;

	protected MessageEvent() {
	}

	private MessageEvent(MessageEventStep step, LocalDateTime at, Map<String, Object> meta) {
		this.step = step;
		this.at = at;
		this.meta = meta;
	}

	static MessageEvent of(MessageEventStep step, LocalDateTime at) {
		return new MessageEvent(step, at, null);
	}

	static MessageEvent played(LocalDateTime at, int count) {
		return new MessageEvent(MessageEventStep.PLAYED, at, new HashMap<>(Map.of("count", count)));
	}

	/**
	 * 두 번째 재생 보고부터는 줄을 새로 만들지 않고 횟수만 갱신한다
	 * ("처음 보고 때 타임라인에 PLAYED를 남기고 이후에는 횟수만 갱신" — 진동 재생 보고 API).
	 */
	void updateCount(int count) {
		if (meta == null) {
			meta = new HashMap<>();
		}
		meta.put("count", count);
	}

	public UUID getId() {
		return id;
	}

	public MessageEventStep getStep() {
		return step;
	}

	public LocalDateTime getAt() {
		return at;
	}

	public Map<String, Object> getMeta() {
		return meta;
	}
}
