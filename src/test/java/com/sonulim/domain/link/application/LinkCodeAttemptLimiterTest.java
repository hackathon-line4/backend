package com.sonulim.domain.link.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.support.MutableClock;

class LinkCodeAttemptLimiterTest {

	private static final UUID USER = UUID.randomUUID();

	private final MutableClock clock = new MutableClock(Instant.parse("2026-11-08T00:00:00Z"));
	private final LinkCodeAttemptLimiter limiter = new LinkCodeAttemptLimiter(clock);

	@Test
	void allowsAttemptAfterNineFailures() {
		failTimes(USER, 9);

		assertThatCode(() -> limiter.checkNotBlocked(USER)).doesNotThrowAnyException();
	}

	@Test
	void blocksAfterTenFailuresWithinTenMinutes() {
		failTimes(USER, 10);

		assertThatThrownBy(() -> limiter.checkNotBlocked(USER))
				.isInstanceOf(SonulimException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOO_MANY_ATTEMPTS)
				.hasFieldOrPropertyWithValue("details", Map.of("retryAfterSec", 600L));
	}

	@Test
	void reportsTimeLeftInWindowAsRetryAfter() {
		limiter.recordFailure(USER);
		clock.advance(Duration.ofMinutes(5));
		failTimes(USER, 9);

		assertThatThrownBy(() -> limiter.checkNotBlocked(USER))
				.hasFieldOrPropertyWithValue("details", Map.of("retryAfterSec", 300L));
	}

	@Test
	void unblocksWhenWindowEnds() {
		failTimes(USER, 10);
		clock.advance(Duration.ofMinutes(10));

		assertThatCode(() -> limiter.checkNotBlocked(USER)).doesNotThrowAnyException();
	}

	@Test
	void startsNewCountWhenWindowHasPassed() {
		failTimes(USER, 9);
		clock.advance(Duration.ofMinutes(10));
		limiter.recordFailure(USER);

		assertThatCode(() -> limiter.checkNotBlocked(USER)).doesNotThrowAnyException();
	}

	@Test
	void countsEachUserSeparately() {
		failTimes(USER, 10);

		assertThatCode(() -> limiter.checkNotBlocked(UUID.randomUUID())).doesNotThrowAnyException();
	}

	private void failTimes(UUID userId, int times) {
		for (int i = 0; i < times; i++) {
			limiter.recordFailure(userId);
		}
	}
}
