package com.sonulim.domain.recipient.application;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sonulim.domain.recipient.domain.HelpRequest;
import com.sonulim.domain.recipient.domain.HelpRequestRepository;
import com.sonulim.domain.recipient.domain.LocationStatus;
import com.sonulim.global.push.LoggingPushSender;
import com.sonulim.global.push.PushType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 타이머는 커밋된 상태를 보므로 @Transactional로 묶지 않고 쓴 데이터를 직접 지운다(MessageTimerTest와 같은 방식).
 */
@SpringBootTest
class HelpRequestRenotifyTest {

	@Autowired
	private HelpRequestRepository helpRequestRepository;

	@Autowired
	private HelpRequestRenotifyScheduler scheduler;

	@Autowired
	private LoggingPushSender pushSender;

	@Autowired
	private RecipientProperties properties;

	@BeforeEach
	void clear() {
		helpRequestRepository.deleteAll();
		pushSender.clear();
	}

	private HelpRequest persistedRequest(LocalDateTime sentAt) {
		return helpRequestRepository.save(HelpRequest.send(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				sentAt, LocationStatus.NOT_SHARED, null));
	}

	@Test
	void unopenedRequestIsNotifiedAgain() {
		persistedRequest(LocalDateTime.now().minusMinutes(5));

		scheduler.sweep();

		assertThat(pushSender.sent()).singleElement()
				.satisfies(message -> assertThat(message.type()).isEqualTo(PushType.HELP_REQUEST));
		assertThat(helpRequestRepository.findAll().get(0).getRenotifyCount()).isEqualTo(1);
	}

	@Test
	void requestJustSentWaitsForTheInterval() {
		persistedRequest(LocalDateTime.now());

		scheduler.sweep();

		assertThat(pushSender.sent()).isEmpty();
	}

	@Test
	void openedRequestStopsBeingNotified() {
		HelpRequest request = persistedRequest(LocalDateTime.now().minusMinutes(5));
		request.markOpened(LocalDateTime.now());
		helpRequestRepository.save(request);

		scheduler.sweep();

		assertThat(pushSender.sent()).isEmpty();
		assertThat(helpRequestRepository.findAll().get(0).getRenotifyCount()).isZero();
	}

	@Test
	void renotifyStopsAtTheLimit() {
		// 한도를 넘길 만큼 오래 묵은 요청이라 훑을 때마다 한 번씩 보낼 조건이 된다
		persistedRequest(LocalDateTime.now().minusHours(1));

		for (int attempt = 0; attempt < properties.renotifyLimit() + 3; attempt++) {
			scheduler.sweep();
		}

		assertThat(pushSender.sent()).hasSize(properties.renotifyLimit());
		assertThat(helpRequestRepository.findAll().get(0).getRenotifyCount())
				.isEqualTo(properties.renotifyLimit());
	}
}
