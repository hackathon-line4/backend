package com.sonulim.domain.settings.application;

import com.sonulim.domain.settings.domain.LocationSharing;
import com.sonulim.domain.vibration.domain.VibrationSettings;

public record PartnerSettings(VibrationSettings vibration, LocationSharing locationSharing) {
}
