package com.sonulim.domain.settings.application;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.settings.domain.LocationSharing;
import com.sonulim.domain.settings.domain.LocationSharingRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 다른 도메인이 쓰는 읽기 전용 창구(#88).
 */
@SpringBootTest
@Transactional
class LocationSharingQueryTest {

	@Autowired
	private LocationSharingService locationSharingService;

	@Autowired
	private LocationSharingRepository locationSharingRepository;

	private UUID saveSharing(boolean enabled) {
		UUID userId = UUID.randomUUID();
		LocationSharing sharing = LocationSharing.defaults(userId);
		sharing.change(enabled, LocalDateTime.now());
		locationSharingRepository.saveAndFlush(sharing);
		return userId;
	}

	@Test
	void sharingIsReportedForAnotherUserWithoutAskingTheirRole() {
		assertThat(locationSharingService.isSharingEnabled(saveSharing(true))).isTrue();
		assertThat(locationSharingService.isSharingEnabled(saveSharing(false))).isFalse();
	}

	@Test
	void aUserWhoNeverTurnedItOnCountsAsOff() {
		// 켠 적이 없으면 꺼진 것이다 — 기본값이 없다고 열어 주면 안 된다
		assertThat(locationSharingService.isSharingEnabled(UUID.randomUUID())).isFalse();
	}
}
