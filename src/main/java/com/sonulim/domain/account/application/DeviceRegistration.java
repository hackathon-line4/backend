package com.sonulim.domain.account.application;

import com.sonulim.domain.account.domain.Platform;

public record DeviceRegistration(Platform platform, String pushToken, String appVersion,
		boolean supportsVibrationIntensity) {
}
