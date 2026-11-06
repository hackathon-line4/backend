package com.sonulim.domain.settings.application;

import org.springframework.stereotype.Service;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.settings.domain.NotificationSettings;
import com.sonulim.domain.settings.domain.NotificationSettingsRepository;
import com.sonulim.global.security.AuthenticatedUser;

@Service
public class NotificationSettingsService {

	private final NotificationSettingsRepository notificationSettingsRepository;

	public NotificationSettingsService(NotificationSettingsRepository notificationSettingsRepository) {
		this.notificationSettingsRepository = notificationSettingsRepository;
	}

	/**
	 * 저장된 값이 없으면(기본값 생성 전에 역할을 고른 계정 등) 기본값을 돌려준다 — 조회만으로는 저장하지 않는다.
	 */
	public NotificationSettings get(AuthenticatedUser user) {
		RoleRequirement.require(user, Role.GUARDIAN);
		return notificationSettingsRepository.findById(user.userId())
				.orElseGet(() -> NotificationSettings.defaults(user.userId()));
	}
}
