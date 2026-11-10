package com.sonulim.domain.recipient.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 당사자가 보낸 것(신호·도움 요청)을 보호자가 받고 열어 본 시각.
 * 두 기록이 같은 규칙을 쓰므로 한곳에 둔다 — 규칙이 미묘해서 두 군데에 적으면 한쪽만 틀리기 쉽다.
 */
@Embeddable
public class GuardianReceipt {

	@Column(name = "guardian_delivered_at")
	private LocalDateTime deliveredAt;

	@Column(name = "guardian_opened_at")
	private LocalDateTime openedAt;

	/** 처음 시각만 기록한다(서버 시각). 같은 보고가 여러 번 와도 값이 밀리지 않는다. */
	void markDelivered(LocalDateTime now) {
		if (deliveredAt == null) {
			deliveredAt = now;
		}
	}

	/**
	 * 열람을 기록한다. 도착 보고를 놓쳐 열람이 먼저 오면 도착도 같은 시각으로 채운다
	 * ("OPENED가 먼저 오면 DELIVERED도 같은 시각으로" — 도착·열람 기록 API).
	 *
	 * @return 이번이 첫 열람이면 true. 도움 요청의 재알림을 멈추는 신호로 쓴다
	 */
	boolean markOpened(LocalDateTime now) {
		if (openedAt != null) {
			return false;
		}
		markDelivered(now);
		openedAt = now;
		return true;
	}

	public LocalDateTime getDeliveredAt() {
		return deliveredAt;
	}

	public LocalDateTime getOpenedAt() {
		return openedAt;
	}
}
