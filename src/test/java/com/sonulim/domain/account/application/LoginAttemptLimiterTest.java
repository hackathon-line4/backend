package com.sonulim.domain.account.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.support.MutableClock;

class LoginAttemptLimiterTest {

	private static final String EMAIL = "user@example.com";

	private final MutableClock clock = new MutableClock(Instant.parse("2026-11-06T00:00:00Z"));
	private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

	@Test
	void allowsLoginAfterFourFailures() {
		failTimes(EMAIL, 4);

		assertThatCode(() -> limiter.checkNotLocked(EMAIL)).doesNotThrowAnyException();
	}

	@Test
	void locksForTenMinutesAfterFiveFailuresWithinTenMinutes() {
		failTimes(EMAIL, 5);

		assertThatThrownBy(() -> limiter.checkNotLocked(EMAIL))
				.isInstanceOf(SonulimException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOO_MANY_ATTEMPTS)
				.hasFieldOrPropertyWithValue("details", Map.of("retryAfterSec", 600L));
	}

	@Test
	void reportsRemainingLockTimeAsRetryAfter() {
		failTimes(EMAIL, 5);
		clock.advance(Duration.ofMinutes(3));

		assertThatThrownBy(() -> limiter.checkNotLocked(EMAIL))
				.hasFieldOrPropertyWithValue("details", Map.of("retryAfterSec", 420L));
	}

	@Test
	void unlocksWhenLockDurationPasses() {
		failTimes(EMAIL, 5);
		clock.advance(Duration.ofMinutes(10));

		assertThatCode(() -> limiter.checkNotLocked(EMAIL)).doesNotThrowAnyException();
	}

	@Test
	void startsNewCountWhenFailuresAreTenMinutesApart() {
		failTimes(EMAIL, 4);
		clock.advance(Duration.ofMinutes(10));
		failTimes(EMAIL, 1);

		assertThatCode(() -> limiter.checkNotLocked(EMAIL)).doesNotThrowAnyException();
	}

	@Test
	void successfulLoginResetsFailures() {
		failTimes(EMAIL, 4);
		limiter.recordSuccess(EMAIL);
		failTimes(EMAIL, 4);

		assertThatCode(() -> limiter.checkNotLocked(EMAIL)).doesNotThrowAnyException();
	}

	@Test
	void countsFailuresPerEmail() {
		failTimes("other@example.com", 5);

		assertThatCode(() -> limiter.checkNotLocked(EMAIL)).doesNotThrowAnyException();
	}

	private void failTimes(String email, int times) {
		for (int i = 0; i < times; i++) {
			limiter.recordFailure(email);
		}
	}
}
