package com.sonulim.domain.account.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.domain.Device;
import com.sonulim.domain.account.domain.DeviceRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

@Service
public class DeviceService {

	private final DeviceRepository deviceRepository;

	public DeviceService(DeviceRepository deviceRepository) {
		this.deviceRepository = deviceRepository;
	}

	/**
	 * 기기 등록(upsert). 같은 푸시 토큰을 가진 다른 기기 행(기기를 넘겨받은 경우)과 이 사용자의 다른 기기를 지운다.
	 * 사용자당 활성 기기 1대는 MVP 임시값이다(docs/domains/account.md Pending Decisions).
	 */
	@Transactional
	public void register(UUID userId, String installationId, DeviceRegistration registration) {
		if (installationId.length() > Device.INSTALLATION_ID_MAX_LENGTH) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED,
					"installationId는 " + Device.INSTALLATION_ID_MAX_LENGTH + "자 이하여야 해요", "installationId");
		}
		deviceRepository.deleteOtherDevicesWithPushToken(registration.pushToken(), installationId);
		deviceRepository.deleteOtherDevicesOfUser(userId, installationId);

		deviceRepository.findById(installationId).ifPresentOrElse(
				device -> device.update(userId, registration.platform(), registration.pushToken(),
						registration.appVersion(), registration.supportsVibrationIntensity()),
				() -> deviceRepository.save(Device.register(installationId, userId, registration.platform(),
						registration.pushToken(), registration.appVersion(),
						registration.supportsVibrationIntensity())));
	}
}
