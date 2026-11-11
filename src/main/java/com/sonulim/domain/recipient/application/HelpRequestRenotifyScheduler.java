package com.sonulim.domain.recipient.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.recipient.domain.HelpRequest;
import com.sonulim.domain.recipient.domain.HelpRequestRepository;
import com.sonulim.global.push.PushMessage;
import com.sonulim.global.push.PushSender;
import com.sonulim.global.push.PushType;

/**
 * 보호자가 열어 볼 때까지 도움 요청을 다시 알린다.
 *
 * <p>도움 요청은 당사자가 3초를 눌러야 보내는 신호라, 한 번 놓치면 그대로 묻힌다. 그래서 열람이 기록될 때까지
 * 일정 간격으로 다시 알리고, 열람되면 멈춘다(서버 내부 처리).
 *
 * <p>ponytail: 짧은 주기 폴링이다. 아직 열어 보지 않은 요청만 훑으므로 대상이 적다.
 */
@Component
@EnableConfigurationProperties(RecipientProperties.class)
public class HelpRequestRenotifyScheduler {

	private static final Logger log = LoggerFactory.getLogger(HelpRequestRenotifyScheduler.class);

	private final HelpRequestRepository helpRequestRepository;
	private final PushSender pushSender;
	private final RecipientProperties properties;
	private final Clock clock;

	public HelpRequestRenotifyScheduler(HelpRequestRepository helpRequestRepository, PushSender pushSender,
			RecipientProperties properties, Clock clock) {
		this.helpRequestRepository = helpRequestRepository;
		this.pushSender = pushSender;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${sonulim.recipient.renotify-sweep-interval-ms}")
	@Transactional
	public void sweep() {
		LocalDateTime now = LocalDateTime.now(clock);
		List<HelpRequest> waiting = helpRequestRepository
				.findByReceiptOpenedAtIsNullAndRenotifyCountLessThan(properties.renotifyLimit());
		int sent = 0;
		for (HelpRequest request : waiting) {
			if (!isDue(request, now)) {
				continue;
			}
			request.countRenotify();
			pushSender.send(PushMessage.to(request.getGuardianId(), PushType.HELP_REQUEST)
					.with("helpRequestId", request.getId().toString())
					.with("linkId", request.getLinkId().toString())
					.build());
			sent++;
		}
		if (sent > 0) {
			log.debug("도움 요청 재알림 {}건", sent);
		}
	}

	/**
	 * 보낸 뒤 간격만큼 지날 때마다 한 번씩. 보낸 시각과 지금까지 알린 횟수만으로 계산해 마지막 알림 시각을 따로 두지 않는다.
	 */
	private boolean isDue(HelpRequest request, LocalDateTime now) {
		LocalDateTime nextAt = request.getSentAt()
				.plus(properties.renotifyInterval().multipliedBy(request.getRenotifyCount() + 1L));
		return !nextAt.isAfter(now);
	}
}
