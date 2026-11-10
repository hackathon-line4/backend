package com.sonulim.domain.account.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.sonulim.domain.account.application.DeviceRegistration;
import com.sonulim.domain.account.domain.Platform;

// platform은 RoleRequest와 같은 이유로 문자열로 받아 VALIDATION_FAILED(field=platform)로 응답한다.
public record DeviceRequest(
		@NotNull @Pattern(regexp = "IOS|ANDROID", message = "platform은 IOS 또는 ANDROID 중 하나여야 해요") String platform,
		@NotBlank @Size(max = 512) String pushToken,
		@NotBlank @Size(max = 32) String appVersion,
		@NotNull Boolean supportsVibrationIntensity) {

	DeviceRegistration toRegistration() {
		return new DeviceRegistration(Platform.valueOf(platform), pushToken, appVersion, supportsVibrationIntensity);
	}
}
