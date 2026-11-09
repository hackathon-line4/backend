package com.sonulim.domain.settings.application;

// null인 값은 바꾸지 않는다(PATCH). helpRequestAlert는 바꿀 수 없는 값이라, 보냈는지만 확인한다.
public record NotificationSettingsChange(Boolean touchResponseAlert, Boolean noResponseAlert,
		Integer responseWaitMinutes, boolean helpRequestAlertSent) {
}
