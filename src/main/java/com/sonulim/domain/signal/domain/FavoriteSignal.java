package com.sonulim.domain.signal.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 홈의 '자주 쓰는 신호'(S-4) — "서버 내부 처리" 문서의 favorite_signals 테이블
 * (link_id, signal_id, position, 최대 6). Signal과는 별도 테이블이다 — 1:1 연결의
 * 신호별 즐겨찾기 여부가 아니라 "이 연결이 고른 최대 6개의 순서 있는 목록"이라
 * link_id·signal_id·position을 묶는 전용 모델이 맞다.
 */
@Entity
@Table(name = "favorite_signals", uniqueConstraints = {
		@UniqueConstraint(name = "uk_favorite_signals_link_signal", columnNames = { "link_id", "signal_id" }),
		@UniqueConstraint(name = "uk_favorite_signals_link_position", columnNames = { "link_id", "position" }) })
public class FavoriteSignal {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "link_id", nullable = false)
	private UUID linkId;

	@Column(name = "signal_id", nullable = false)
	private UUID signalId;

	@Column(nullable = false)
	private int position;

	protected FavoriteSignal() {
	}

	public FavoriteSignal(UUID linkId, UUID signalId, int position) {
		this.linkId = linkId;
		this.signalId = signalId;
		this.position = position;
	}

	public UUID getId() {
		return id;
	}

	public UUID getLinkId() {
		return linkId;
	}

	public UUID getSignalId() {
		return signalId;
	}

	public int getPosition() {
		return position;
	}
}
