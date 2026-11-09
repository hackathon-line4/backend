package com.sonulim.domain.location.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LastLocationTest {

	private static final LocalDateTime CAPTURED_AT = LocalDateTime.of(2026, 11, 4, 17, 55, 1);

	private static LastLocation cityHall() {
		return LastLocation.of(UUID.randomUUID(), new BigDecimal("37.566500"), new BigDecimal("126.978000"),
				25, CAPTURED_AT);
	}

	@Test
	void aNewLocationHasNoAddressUntilTheServerFillsItIn() {
		LastLocation location = cityHall();

		assertThat(location.getLatitude()).isEqualByComparingTo("37.566500");
		assertThat(location.getAddress()).isNull();

		location.fillAddress("서울 중구 세종대로 110");
		assertThat(location.getAddress()).isEqualTo("서울 중구 세종대로 110");
	}

	@Test
	void overwritingDropsTheOldAddress() {
		LastLocation location = cityHall();
		location.fillAddress("서울 중구 세종대로 110");

		location.overwriteWith(new BigDecimal("35.179554"), new BigDecimal("129.075641"), 18,
				CAPTURED_AT.plusHours(3));

		// 좌표가 바뀌었는데 옛 주소가 남아 있으면 지도와 주소가 어긋난다
		assertThat(location.getAddress()).isNull();
		assertThat(location.getLatitude()).isEqualByComparingTo("35.179554");
		assertThat(location.getCapturedAt()).isEqualTo(CAPTURED_AT.plusHours(3));
	}
}
