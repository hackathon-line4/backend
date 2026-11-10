package com.sonulim.global.push;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushMessageTest {

	@Test
	void everyPushCarriesItsTypeAndTheIdsNeededToOpenAScreen() {
		UUID messageId = UUID.randomUUID();
		PushMessage push = PushMessage.to(UUID.randomUUID(), PushType.NO_RESPONSE)
				.with("linkId", "lnk_1")
				.with("messageId", messageId.toString())
				.build();

		assertThat(push.data())
				.containsEntry("type", "NO_RESPONSE")
				.containsEntry("linkId", "lnk_1")
				.containsEntry("messageId", messageId.toString());
	}

	@Test
	void keysWithNoValueAreLeftOutSoTheAppCanBranchOnTheirPresence() {
		PushMessage push = PushMessage.to(UUID.randomUUID(), PushType.LINK_RESULT)
				.with("linkRequestId", "lrq_7")
				.with("status", null)
				.build();

		assertThat(push.data()).containsOnlyKeys("type", "linkRequestId");
	}

	@Test
	void theDataCannotBeChangedAfterBuilding() {
		PushMessage push = PushMessage.to(UUID.randomUUID(), PushType.SIGNAL).build();

		assertThat(push.data()).isUnmodifiable();
	}

	@Test
	void howEachTypeIsDeliveredFollowsTheSpec() {
		// 앱이 직접 리듬을 울려야 하는 것은 표시 없는 데이터 메시지다
		assertThat(PushType.SIGNAL.delivery()).isEqualTo(PushType.Delivery.DATA);
		assertThat(PushType.PRACTICE.delivery()).isEqualTo(PushType.Delivery.DATA);
		// 받으면 앱이 API를 부르는 것은 조용히 간다
		assertThat(PushType.CONNECTION_CHECK.delivery()).isEqualTo(PushType.Delivery.SILENT);
		assertThat(PushType.LOCATION_REQUEST.delivery()).isEqualTo(PushType.Delivery.SILENT);
		// 도움 요청은 가장 강하게
		assertThat(PushType.HELP_REQUEST.delivery()).isEqualTo(PushType.Delivery.CRITICAL);
	}

	@Test
	void onlyTheHelpRequestCannotBeTurnedOff() {
		assertThat(PushType.HELP_REQUEST.isAlwaysDelivered()).isTrue();
		assertThat(PushType.NO_RESPONSE.isAlwaysDelivered()).isFalse();
		assertThat(PushType.TOUCH_RESPONSE.isAlwaysDelivered()).isFalse();
	}
}
