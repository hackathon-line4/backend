package com.sonulim.domain.location.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocationRequestTest {

	private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 11, 4, 18, 0, 0);
	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	private static LocationRequest opened() {
		return LocationRequest.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), REQUESTED_AT, TIMEOUT);
	}

	@Test
	void aNewRequestWaitsWithADeadline() {
		LocationRequest request = opened();

		assertThat(request.getStatus()).isEqualTo(LocationRequestStatus.PENDING);
		assertThat(request.getTimeoutAt()).isEqualTo(REQUESTED_AT.plusSeconds(30));
		assertThat(request.getFailureReason()).isNull();
		assertThat(request.isPending()).isTrue();
	}

	@Test
	void theRecipientSendingALocationCompletesTheRequest() {
		LocationRequest request = opened();

		assertThat(request.markFulfilled()).isTrue();
		assertThat(request.getStatus()).isEqualTo(LocationRequestStatus.FULFILLED);
	}

	@Test
	void failureKeepsWhyItFailed() {
		LocationRequest request = opened();

		assertThat(request.markFailed(LocationFailureReason.PERMISSION_DENIED)).isTrue();
		assertThat(request.getStatus()).isEqualTo(LocationRequestStatus.FAILED);
		assertThat(request.getFailureReason()).isEqualTo(LocationFailureReason.PERMISSION_DENIED);
	}

	@Test
	void aLocationArrivingAfterTheDeadlineDoesNotRevertTheFailure() {
		LocationRequest request = opened();
		request.markTimedOut();

		// 보호자 화면은 이미 '실패'를 보여 줬다 — 상태를 되돌리지 않는다.
		// 다만 늦게 온 위치 자체는 마지막 위치에 반영한다(그 판단은 호출하는 쪽이 한다).
		assertThat(request.markFulfilled()).isFalse();
		assertThat(request.getStatus()).isEqualTo(LocationRequestStatus.TIMED_OUT);
	}

	@Test
	void aRequestThatAlreadyFinishedIsNotTimedOutLater() {
		LocationRequest request = opened();
		request.markFulfilled();

		assertThat(request.markTimedOut()).isFalse();
		assertThat(request.getStatus()).isEqualTo(LocationRequestStatus.FULFILLED);
	}

	@Test
	void onlyTheFirstAnswerCounts() {
		LocationRequest request = opened();
		request.markFulfilled();

		assertThat(request.markFailed(LocationFailureReason.POSITION_UNAVAILABLE)).isFalse();
		assertThat(request.getFailureReason()).isNull();
	}
}
