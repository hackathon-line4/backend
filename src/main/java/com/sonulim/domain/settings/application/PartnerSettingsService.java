package com.sonulim.domain.settings.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.settings.domain.LocationSharing;
import com.sonulim.domain.settings.domain.LocationSharingRepository;
import com.sonulim.domain.vibration.application.VibrationSettingsService;
import com.sonulim.domain.vibration.domain.VibrationSettings;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.linkaccess.LinkAccessGuard;
import com.sonulim.global.linkaccess.LinkMembers;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 보호자 설정 화면에 보여 줄 당사자의 진동 설정·위치 공유 상태(읽기 전용, C-1·T-3). 보호자는 바꿀 수 없다.
 */
@Service
public class PartnerSettingsService {

	private final LinkAccessGuard linkAccessGuard;
	private final VibrationSettingsService vibrationSettingsService;
	private final LocationSharingRepository locationSharingRepository;

	public PartnerSettingsService(LinkAccessGuard linkAccessGuard, VibrationSettingsService vibrationSettingsService,
			LocationSharingRepository locationSharingRepository) {
		this.linkAccessGuard = linkAccessGuard;
		this.vibrationSettingsService = vibrationSettingsService;
		this.locationSharingRepository = locationSharingRepository;
	}

	/**
	 * 남의 연결·없는 연결·형식이 틀린 id는 모두 404다 — 연결이 있는지 자체를 드러내지 않는다(link API와 같음).
	 * 저장된 값이 없으면 각 설정의 기본값을 돌려준다.
	 */
	public PartnerSettings get(AuthenticatedUser user, String linkId) {
		RoleRequirement.require(user, Role.GUARDIAN);
		LinkMembers members = linkAccessGuard.requireMembers(parseLinkId(linkId), user.userId());
		UUID recipientId = members.recipientId();

		VibrationSettings vibration = vibrationSettingsService.get(recipientId);
		LocationSharing locationSharing = locationSharingRepository.findById(recipientId)
				.orElseGet(() -> LocationSharing.defaults(recipientId));
		return new PartnerSettings(vibration, locationSharing);
	}

	private static UUID parseLinkId(String linkId) {
		try {
			return UUID.fromString(linkId);
		} catch (IllegalArgumentException exception) {
			throw new SonulimException(ErrorCode.NOT_FOUND, "연결을 찾을 수 없어요");
		}
	}
}
