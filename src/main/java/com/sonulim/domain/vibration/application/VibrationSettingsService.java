package com.sonulim.domain.vibration.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sonulim.domain.vibration.domain.VibrationSettings;
import com.sonulim.domain.vibration.domain.VibrationSettingsRepository;

@Service
public class VibrationSettingsService {

	private final VibrationSettingsRepository vibrationSettingsRepository;

	public VibrationSettingsService(VibrationSettingsRepository vibrationSettingsRepository) {
		this.vibrationSettingsRepository = vibrationSettingsRepository;
	}

	/**
	 * 저장한 적이 없으면 기본값(updatedAt: null)을 돌려준다. 보호자·당사자 공통.
	 */
	public VibrationSettings get(UUID userId) {
		return vibrationSettingsRepository.findById(userId).orElseGet(() -> VibrationSettings.defaults(userId));
	}
}
