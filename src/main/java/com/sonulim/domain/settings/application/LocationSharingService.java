package com.sonulim.domain.settings.application;

import org.springframework.stereotype.Service;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.settings.domain.LocationSharing;
import com.sonulim.domain.settings.domain.LocationSharingRepository;
import com.sonulim.global.security.AuthenticatedUser;

@Service
public class LocationSharingService {

	private final LocationSharingRepository locationSharingRepository;

	public LocationSharingService(LocationSharingRepository locationSharingRepository) {
		this.locationSharingRepository = locationSharingRepository;
	}

	/**
	 * 저장된 값이 없으면 기본값(꺼짐)을 돌려준다 — 조회만으로는 저장하지 않는다.
	 */
	public LocationSharing get(AuthenticatedUser user) {
		RoleRequirement.require(user, Role.RECIPIENT);
		return locationSharingRepository.findById(user.userId())
				.orElseGet(() -> LocationSharing.defaults(user.userId()));
	}
}
