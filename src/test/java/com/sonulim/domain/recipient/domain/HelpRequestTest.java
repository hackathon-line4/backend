package com.sonulim.domain.recipient.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HelpRequestTest {

	private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 11, 4, 17, 55, 2);

	private static CapturedLocation seoulCityHall() {
		return new CapturedLocation(new BigDecimal("37.566500"), new BigDecimal("126.978000"), 25,
				SENT_AT.minusSeconds(1));
	}

	private static HelpRequest sent(LocationStatus status, CapturedLocation location) {
		return HelpRequest.send(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), SENT_AT, status, location);
	}

	@Test
	void aRequestWithLocationKeepsIt() {
		HelpRequest request = sent(LocationStatus.ATTACHED, seoulCityHall());

		assertThat(request.getLocationStatus()).isEqualTo(LocationStatus.ATTACHED);
		assertThat(request.getLocation().getLatitude()).isEqualByComparingTo("37.566500");
		// 주소는 서버가 나중에 채운다
		assertThat(request.getLocation().getAddress()).isNull();
	}

	@Test
	void locationIsNotStoredWhenSharingIsOff() {
		// 공유가 꺼져 있으면 좌표가 와도 저장하지 않는다
		HelpRequest request = sent(LocationStatus.NOT_SHARED, seoulCityHall());

		assertThat(request.getLocationStatus()).isEqualTo(LocationStatus.NOT_SHARED);
		assertThat(request.getLocation()).isNull();
	}

	@Test
	void aPendingRequestCanGetItsLocationLater() {
		HelpRequest request = sent(LocationStatus.PENDING, null);

		request.attachLocation(LocationStatus.ATTACHED, seoulCityHall());

		assertThat(request.getLocationStatus()).isEqualTo(LocationStatus.ATTACHED);
		assertThat(request.getLocation().getAccuracyM()).isEqualTo(25);
	}

	@Test
	void aPendingRequestThatNeverGetsALocationBecomesUnavailable() {
		HelpRequest request = sent(LocationStatus.PENDING, null);

		request.attachLocation(LocationStatus.UNAVAILABLE, null);

		assertThat(request.getLocationStatus()).isEqualTo(LocationStatus.UNAVAILABLE);
		assertThat(request.getLocation()).isNull();
	}

	@Test
	void locationCannotBeChangedOnceSet() {
		HelpRequest request = sent(LocationStatus.ATTACHED, seoulCityHall());

		assertThatThrownBy(() -> request.attachLocation(LocationStatus.ATTACHED, seoulCityHall()))
				.isInstanceOf(SonulimException.class)
				.extracting(thrown -> ((SonulimException) thrown).getErrorCode())
				.isEqualTo(ErrorCode.LOCATION_ALREADY_SET);
	}

	@Test
	void addressIsFilledInAfterwards() {
		HelpRequest request = sent(LocationStatus.ATTACHED, seoulCityHall());

		request.fillAddress("서울 중구 세종대로 110");

		assertThat(request.getLocation().getAddress()).isEqualTo("서울 중구 세종대로 110");
	}

	@Test
	void fillingAnAddressOnARequestWithoutLocationDoesNothing() {
		HelpRequest request = sent(LocationStatus.PENDING, null);

		request.fillAddress("서울 중구 세종대로 110");

		assertThat(request.getLocation()).isNull();
	}

	@Test
	void openingTheRequestStopsTheRenotifications() {
		HelpRequest request = sent(LocationStatus.PENDING, null);
		request.countRenotify();
		request.countRenotify();

		boolean firstOpen = request.markOpened(SENT_AT.plusSeconds(38));
		request.countRenotify(); // 열람 뒤에는 더 이상 세지 않는다

		assertThat(firstOpen).isTrue();
		assertThat(request.getRenotifyCount()).isEqualTo(2);
		assertThat(request.isOpened()).isTrue();
	}

	@Test
	void onlyTheFirstOpenSignalsThatRenotificationShouldStop() {
		HelpRequest request = sent(LocationStatus.PENDING, null);
		request.markOpened(SENT_AT.plusSeconds(38));

		// 두 번째 열람은 '확인함'을 다시 보내지 않아야 한다
		assertThat(request.markOpened(SENT_AT.plusMinutes(2))).isFalse();
		assertThat(request.getGuardianOpenedAt()).isEqualTo(SENT_AT.plusSeconds(38));
	}
}
