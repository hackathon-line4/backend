package com.sonulim.domain.settings.api;

import com.sonulim.domain.settings.domain.NotificationSettings;

public record NotificationSettingsResponse(boolean touchResponseAlert, boolean noResponseAlert,
		int responseWaitMinutes, boolean helpRequestAlert) {

	public static NotificationSettingsResponse from(NotificationSettings settings) {
		// 도움 요청 알림은 끌 수 없어 항상 true로 내려준다.
		return new NotificationSettingsResponse(settings.isTouchResponseAlert(), settings.isNoResponseAlert(),
				settings.getResponseWaitMinutes(), true);
	}
}
