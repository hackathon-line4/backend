package com.sonulim.domain.vibration.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	/**
	 * 통째로 저장한다. 응답의 updatedAt을 채우려고 바로 flush한다(@LastModifiedDate는 flush 때 기록).
	 * 당사자가 바꾸면 보호자에게 settings.updated 이벤트를 보내는 부분은 SSE·link 구현 후 붙인다.
	 */
	@Transactional
	public VibrationSettings save(UUID userId, VibrationSettingsChange change) {
		VibrationSettings settings = vibrationSettingsRepository.findById(userId)
				.orElseGet(() -> VibrationSettings.defaults(userId));
		settings.change(change.intensity(), change.length(), change.repeatCount());
		return vibrationSettingsRepository.saveAndFlush(settings);
	}
}
