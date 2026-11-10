package com.sonulim.domain.link.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

/**
 * 연결 코드 무작위 대입 방지: 사용자당 10분에 10회 틀리면 TOO_MANY_ATTEMPTS(Notion API 명세).
 *
 * 첫 실패부터 10분을 한 구간으로 세고, 그 안에 10회 틀리면 구간이 끝날 때까지 막는다. 명세에 잠금 시간이 따로 없어서
 * 구간 끝까지로 두었다(docs/domains/link.md Pending Decisions).
 *
 * 서버 메모리에 저장한다(account의 LoginAttemptLimiter와 같은 방식). 재시작하면 초기화되고, 서버를 2대 이상으로 늘리면
 * Redis 같은 공유 저장소로 옮겨야 한다.
 */
@Component
public class LinkCodeAttemptLimiter {

	static final int MAX_FAILURES = 10;
	static final Duration WINDOW = Duration.ofMinutes(10);

	private final Map<UUID, Attempts> attemptsByUser = new ConcurrentHashMap<>();
	private final Clock clock;

	public LinkCodeAttemptLimiter(Clock clock) {
		this.clock = clock;
	}

	public void checkNotBlocked(UUID userId) {
		Attempts attempts = attemptsByUser.get(userId);
		Instant now = clock.instant();
		if (attempts != null && attempts.isBlockedAt(now)) {
			long retryAfterSec = Duration.between(now, attempts.windowEnd()).toSeconds();
			throw new SonulimException(ErrorCode.TOO_MANY_ATTEMPTS, "잠시 후 다시 시도해 주세요", null,
					Map.of("retryAfterSec", retryAfterSec));
		}
	}

	public void recordFailure(UUID userId) {
		Instant now = clock.instant();
		attemptsByUser.compute(userId, (key, previous) -> {
			if (previous == null || previous.isOverAt(now)) {
				return Attempts.firstFailureAt(now);
			}
			return previous.withAnotherFailure();
		});
	}

	private record Attempts(int failures, Instant windowStart) {

		static Attempts firstFailureAt(Instant now) {
			return new Attempts(1, now);
		}

		Instant windowEnd() {
			return windowStart.plus(WINDOW);
		}

		boolean isOverAt(Instant now) {
			return !now.isBefore(windowEnd());
		}

		boolean isBlockedAt(Instant now) {
			return failures >= MAX_FAILURES && !isOverAt(now);
		}

		Attempts withAnotherFailure() {
			return new Attempts(failures + 1, windowStart);
		}
	}
}
