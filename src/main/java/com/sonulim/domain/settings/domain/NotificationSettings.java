package com.sonulim.domain.settings.domain;

import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.sonulim.global.BaseEntity;

/**
 * 보호자의 알림 설정. 도움 요청 알림은 끌 수 없어 저장하지 않는다(응답에는 항상 true).
 */
@Entity
@Table(name = "notification_settings")
public class NotificationSettings extends BaseEntity {

	public static final Set<Integer> ALLOWED_RESPONSE_WAIT_MINUTES = Set.of(5, 10, 30);

	private static final int DEFAULT_RESPONSE_WAIT_MINUTES = 10;

	@Id
	private UUID userId;

	@Column(nullable = false)
	private boolean touchResponseAlert;

	@Column(nullable = false)
	private boolean noResponseAlert;

	@Column(nullable = false)
	private int responseWaitMinutes;

	protected NotificationSettings() {
	}

	private NotificationSettings(UUID userId, boolean touchResponseAlert, boolean noResponseAlert,
			int responseWaitMinutes) {
		this.userId = userId;
		this.touchResponseAlert = touchResponseAlert;
		this.noResponseAlert = noResponseAlert;
		this.responseWaitMinutes = responseWaitMinutes;
	}

	/**
	 * API 명세의 기본값: 터치 응답 알림 켬, 응답 없음 알림 켬, 10분.
	 */
	public static NotificationSettings defaults(UUID userId) {
		return new NotificationSettings(userId, true, true, DEFAULT_RESPONSE_WAIT_MINUTES);
	}

	/**
	 * 보낸 값만 바꾼다(null은 그대로 둠). 대기 시간 허용값 검사는 호출하는 쪽에서 먼저 한다.
	 */
	public void change(Boolean touchResponseAlert, Boolean noResponseAlert, Integer responseWaitMinutes) {
		if (touchResponseAlert != null) {
			this.touchResponseAlert = touchResponseAlert;
		}
		if (noResponseAlert != null) {
			this.noResponseAlert = noResponseAlert;
		}
		if (responseWaitMinutes != null) {
			this.responseWaitMinutes = responseWaitMinutes;
		}
	}

	public UUID getUserId() {
		return userId;
	}

	public boolean isTouchResponseAlert() {
		return touchResponseAlert;
	}

	public boolean isNoResponseAlert() {
		return noResponseAlert;
	}

	public int getResponseWaitMinutes() {
		return responseWaitMinutes;
	}
}
