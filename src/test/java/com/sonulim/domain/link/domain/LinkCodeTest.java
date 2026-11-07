package com.sonulim.domain.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LinkCodeTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 1, 10, 0);

	private final LinkCode linkCode = LinkCode.issue(UUID.randomUUID(), "482913", UUID.randomUUID(), NOW);

	@Test
	void expiresTenMinutesAfterIssue() {
		assertThat(linkCode.getIssuedAt()).isEqualTo(NOW);
		assertThat(linkCode.getExpiresAt()).isEqualTo(NOW.plusMinutes(10));
	}

	@Test
	void isLiveJustBeforeExpiry() {
		assertThat(linkCode.isLive(NOW.plusMinutes(10).minusSeconds(1))).isTrue();
	}

	@Test
	void isExpiredAtExpiryInstant() {
		assertThat(linkCode.isExpired(NOW.plusMinutes(10))).isTrue();
		assertThat(linkCode.isLive(NOW.plusMinutes(10))).isFalse();
	}

	@Test
	void isNotRevokedWhenIssued() {
		assertThat(linkCode.isRevoked()).isFalse();
		assertThat(linkCode.getRevokedAt()).isNull();
	}
}
