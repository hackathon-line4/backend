package com.sonulim.domain.settings.api;

import com.sonulim.domain.settings.application.NotificationSettingsChange;

public record NotificationSettingsUpdateRequest(Boolean touchResponseAlert, Boolean noResponseAlert,
		Integer responseWaitMinutes, Boolean helpRequestAlert) {

	NotificationSettingsChange toChange() {
		return new NotificationSettingsChange(touchResponseAlert, noResponseAlert, responseWaitMinutes,
				helpRequestAlert != null);
	}
}
