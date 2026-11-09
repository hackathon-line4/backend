package com.sonulim.domain.account.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.sonulim.global.BaseEntity;

/**
 * 푸시를 받을 기기. installationId는 앱 설치 때 앱이 한 번 만들어 계속 쓰는 값이라 그대로 기본 키로 쓴다.
 */
@Entity
@Table(name = "devices")
public class Device extends BaseEntity {

	public static final int INSTALLATION_ID_MAX_LENGTH = 64;

	@Id
	@Column(length = INSTALLATION_ID_MAX_LENGTH)
	private String installationId;

	@Column(nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Platform platform;

	@Column(nullable = false, unique = true, length = 512)
	private String pushToken;

	@Column(nullable = false, length = 32)
	private String appVersion;

	@Column(nullable = false)
	private boolean supportsVibrationIntensity;

	private LocalDateTime lastSeenAt;

	protected Device() {
	}

	private Device(String installationId) {
		this.installationId = installationId;
	}

	public static Device register(String installationId, UUID userId, Platform platform, String pushToken,
			String appVersion, boolean supportsVibrationIntensity) {
		Device device = new Device(installationId);
		device.update(userId, platform, pushToken, appVersion, supportsVibrationIntensity);
		return device;
	}

	/**
	 * 같은 설치에서 다른 계정으로 로그인하면 소유자도 바뀐다.
	 */
	public void update(UUID userId, Platform platform, String pushToken, String appVersion,
			boolean supportsVibrationIntensity) {
		this.userId = userId;
		this.platform = platform;
		this.pushToken = pushToken;
		this.appVersion = appVersion;
		this.supportsVibrationIntensity = supportsVibrationIntensity;
	}

	public String getInstallationId() {
		return installationId;
	}

	public UUID getUserId() {
		return userId;
	}

	public Platform getPlatform() {
		return platform;
	}

	public String getPushToken() {
		return pushToken;
	}

	public String getAppVersion() {
		return appVersion;
	}

	public boolean isSupportsVibrationIntensity() {
		return supportsVibrationIntensity;
	}

	public LocalDateTime getLastSeenAt() {
		return lastSeenAt;
	}
}
