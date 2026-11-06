package com.sonulim.domain.account.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

/**
 * 로그인 실패 제한: 같은 이메일로 10분 안에 5번 실패하면 10분 동안 잠근다(팀 확정, docs/domains/account.md).
 *
 * 서버 메모리에 저장한다 — 가입되지 않은 이메일도 똑같이 세어 계정 존재 여부가 드러나지 않게 하기 위해서다.
 * 재시작하면 초기화되고, 서버를 2대 이상으로 늘리면 Redis 같은 공유 저장소로 옮겨야 한다.
 */
@Component
public class LoginAttemptLimiter {

	static final int MAX_FAILURES = 5;
	static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);
	static final Duration LOCK_DURATION = Duration.ofMinutes(10);

	private final Map<String, Attempts> attemptsByEmail = new ConcurrentHashMap<>();
	private final Clock clock;

	public LoginAttemptLimiter(Clock clock) {
		this.clock = clock;
	}

	public void checkNotLocked(String email) {
		Attempts attempts = attemptsByEmail.get(email);
		Instant now = clock.instant();
		if (attempts != null && attempts.isLockedAt(now)) {
			long retryAfterSec = Duration.between(now, attempts.lockedUntil()).toSeconds();
			throw new SonulimException(ErrorCode.TOO_MANY_ATTEMPTS, "잠시 후 다시 시도해 주세요", null,
					Map.of("retryAfterSec", retryAfterSec));
		}
	}

	public void recordFailure(String email) {
		Instant now = clock.instant();
		attemptsByEmail.compute(email, (key, previous) -> {
			if (previous == null || previous.isStaleAt(now)) {
				return Attempts.firstFailureAt(now);
			}
			return previous.withAnotherFailureAt(now);
		});
	}

	public void recordSuccess(String email) {
		attemptsByEmail.remove(email);
	}

	private record Attempts(int failures, Instant windowStart, Instant lockedUntil) {

		static Attempts firstFailureAt(Instant now) {
			return new Attempts(1, now, null);
		}

		boolean isLockedAt(Instant now) {
			return lockedUntil != null && now.isBefore(lockedUntil);
		}

		// 잠금이 끝났거나, 잠기지 않은 채 첫 실패로부터 10분이 지나면 새로 센다.
		boolean isStaleAt(Instant now) {
			if (lockedUntil != null) {
				return !now.isBefore(lockedUntil);
			}
			return !now.isBefore(windowStart.plus(FAILURE_WINDOW));
		}

		Attempts withAnotherFailureAt(Instant now) {
			int nextFailures = failures + 1;
			Instant nextLockedUntil = nextFailures >= MAX_FAILURES ? now.plus(LOCK_DURATION) : null;
			return new Attempts(nextFailures, windowStart, nextLockedUntil);
		}
	}
}
