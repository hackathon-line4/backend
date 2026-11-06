package com.sonulim.domain.settings.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.sonulim.global.BaseEntity;

/**
 * 당사자의 '도움 요청에 위치 보내기' 설정. 응답의 updatedAt은 사용자가 마지막으로 바꾼 시각이라
 * BaseEntity의 updatedAt과 따로 둔다(한 번도 바꾸지 않았으면 null).
 */
@Entity
@Table(name = "location_sharing")
public class LocationSharing extends BaseEntity {

	@Id
	private UUID userId;

	@Column(nullable = false)
	private boolean enabled;

	private LocalDateTime changedAt;

	protected LocationSharing() {
	}

	private LocationSharing(UUID userId, boolean enabled) {
		this.userId = userId;
		this.enabled = enabled;
	}

	/**
	 * API 명세의 기본값: 꺼짐.
	 */
	public static LocationSharing defaults(UUID userId) {
		return new LocationSharing(userId, false);
	}

	public UUID getUserId() {
		return userId;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public LocalDateTime getChangedAt() {
		return changedAt;
	}
}
