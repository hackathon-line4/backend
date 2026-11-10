package com.sonulim.domain.link.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

class LinkRequestTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 1, 11, 0);

	private final LinkCode linkCode = LinkCode.issue(UUID.randomUUID(), "482913", UUID.randomUUID(), NOW);
	private final UUID recipientId = UUID.randomUUID();
	private final LinkRequest request = LinkRequest.request(recipientId, linkCode, UUID.randomUUID(), NOW);

	@Test
	void startsPendingAndExpiresTenMinutesLater() {
		assertThat(request.getStatus()).isEqualTo(LinkRequestStatus.PENDING);
		assertThat(request.getGuardianId()).isEqualTo(linkCode.getGuardianId());
		assertThat(request.getCode()).isEqualTo("482913");
		assertThat(request.getRequestedAt()).isEqualTo(NOW);
		assertThat(request.getExpiresAt()).isEqualTo(NOW.plusMinutes(10));
	}

	@Test
	void doesNotExpireJustBeforeExpiry() {
		assertThat(request.expireIfDue(NOW.plusMinutes(10).minusSeconds(1))).isFalse();
		assertThat(request.getStatus()).isEqualTo(LinkRequestStatus.PENDING);
	}

	@Test
	void expiresAtExpiryInstantWithoutDecisionTime() {
		assertThat(request.expireIfDue(NOW.plusMinutes(10))).isTrue();
		assertThat(request.getStatus()).isEqualTo(LinkRequestStatus.EXPIRED);
		assertThat(request.getDecidedAt()).isNull();
	}

	@Test
	void decidingAfterExpiryFailsWithExpiredStatus() {
		assertThatThrownBy(() -> request.reject(NOW.plusMinutes(10)))
				.isInstanceOf(SonulimException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.LINK_REQUEST_NOT_PENDING)
				.hasFieldOrPropertyWithValue("details", Map.of("status", "EXPIRED"))
				.hasMessage("요청이 만료됐어요");
	}

	@Test
	void approvingRecordsLinkAndDecisionTime() {
		UUID linkId = UUID.randomUUID();

		request.approve(linkId, NOW.plusMinutes(1));

		assertThat(request.getStatus()).isEqualTo(LinkRequestStatus.APPROVED);
		assertThat(request.getLinkId()).isEqualTo(linkId);
		assertThat(request.getDecidedAt()).isEqualTo(NOW.plusMinutes(1));
	}

	@Test
	void decidingTwiceFailsWithCurrentStatus() {
		request.cancel(NOW.plusMinutes(1));

		assertThatThrownBy(() -> request.approve(UUID.randomUUID(), NOW.plusMinutes(2)))
				.isInstanceOf(SonulimException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.LINK_REQUEST_NOT_PENDING)
				.hasFieldOrPropertyWithValue("details", Map.of("status", "CANCELED"))
				.hasMessage("이미 처리된 요청이에요");
	}

	@Test
	void involvesOnlyItsGuardianAndRecipient() {
		assertThat(request.involves(linkCode.getGuardianId())).isTrue();
		assertThat(request.involves(recipientId)).isTrue();
		assertThat(request.involves(UUID.randomUUID())).isFalse();
	}
}
