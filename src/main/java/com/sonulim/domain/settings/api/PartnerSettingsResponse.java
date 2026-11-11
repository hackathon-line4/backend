package com.sonulim.domain.settings.api;

import com.sonulim.domain.settings.application.PartnerSettings;
import com.sonulim.domain.vibration.api.VibrationSettingsResponse;

/**
 * 각 항목의 모양은 당사자 본인이 조회하는 진동 설정·위치 공유 설정 응답과 같다.
 */
public record PartnerSettingsResponse(VibrationSettingsResponse vibration, LocationSharingResponse locationSharing) {

	public static PartnerSettingsResponse from(PartnerSettings settings) {
		return new PartnerSettingsResponse(VibrationSettingsResponse.from(settings.vibration()),
				LocationSharingResponse.from(settings.locationSharing()));
	}
}
