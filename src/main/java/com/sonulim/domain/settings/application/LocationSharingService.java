package com.sonulim.domain.settings.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.application.RoleSelectedEvent;
import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.settings.domain.LocationSharing;
import com.sonulim.domain.settings.domain.LocationSharingRepository;
import com.sonulim.global.security.AuthenticatedUser;

@Service
public class LocationSharingService {

	private final LocationSharingRepository locationSharingRepository;
	private final Clock clock;

	public LocationSharingService(LocationSharingRepository locationSharingRepository, Clock clock) {
		this.locationSharingRepository = locationSharingRepository;
		this.clock = clock;
	}

	/**
	 * 저장된 값이 없으면 기본값(꺼짐)을 돌려준다 — 조회만으로는 저장하지 않는다.
	 */
	public LocationSharing get(AuthenticatedUser user) {
		RoleRequirement.require(user, Role.RECIPIENT);
		return locationSharingRepository.findById(user.userId())
				.orElseGet(() -> LocationSharing.defaults(user.userId()));
	}

	/**
	 * 다른 도메인이 당사자의 위치 공유 상태를 읽는 창구(#88). 보호자가 부르는 위치 API가
	 * "이 당사자가 공유를 켜 뒀는가"를 알아야 하는데, {@link #get(AuthenticatedUser)}은 본인 조회라
	 * 보호자가 부르면 역할 검사에서 막힌다.
	 *
	 * <p>권한은 부르는 쪽이 이미 확인했으므로(연결 구성원인지) 여기서 다시 묻지 않는다.
	 * 저장된 값이 없으면 꺼짐으로 본다 — 켠 적이 없으면 꺼진 것이다.
	 */
	public boolean isSharingEnabled(UUID userId) {
		return locationSharingRepository.findById(userId)
				.map(LocationSharing::isEnabled)
				.orElse(false);
	}

	/**
	 * 당사자 역할을 고르면 기본값(꺼짐)을 만든다. selectRole과 같은 트랜잭션에서 실행된다.
	 */
	@EventListener
	public void createDefaultsForRecipient(RoleSelectedEvent event) {
		if (event.role() == Role.RECIPIENT && !locationSharingRepository.existsById(event.userId())) {
			locationSharingRepository.save(LocationSharing.defaults(event.userId()));
		}
	}

	/**
	 * 끌 때 마지막 위치 삭제(location)와 보호자에게 settings.updated 이벤트(SSE·link)는
	 * 해당 기능이 구현된 뒤 붙인다(docs/domains/settings.md 후속 작업).
	 */
	@Transactional
	public LocationSharing update(AuthenticatedUser user, boolean enabled) {
		RoleRequirement.require(user, Role.RECIPIENT);
		LocationSharing locationSharing = locationSharingRepository.findById(user.userId())
				.orElseGet(() -> locationSharingRepository.save(LocationSharing.defaults(user.userId())));
		locationSharing.change(enabled, LocalDateTime.now(clock));
		return locationSharing;
	}
}
