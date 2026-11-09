package com.sonulim.domain.messaging.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.sonulim.domain.messaging.domain.DeliveryStatus;
import com.sonulim.domain.messaging.domain.Message;
import com.sonulim.domain.messaging.domain.MessageRepository;
import com.sonulim.domain.messaging.domain.ResponseStatus;

/**
 * 기한이 지난 기록을 주기적으로 훑어 판정한다.
 *
 * <p>기한(ackDeadline·waitUntil)을 DB 컬럼으로 들고 있어 서버가 재시작해도 판정이 사라지지 않는다(서버 내부 처리).
 *
 * <p>ponytail: 짧은 주기 폴링이다. 명세가 "지연 큐 또는 짧은 주기 스케줄러"를 모두 허용하므로 새 인프라를 들이지 않았다.
 * 기록이 많아져 매 주기 스캔이 부담되면 지연 큐로 바꾼다.
 */
@Component
@EnableConfigurationProperties(MessagingProperties.class)
public class MessageTimerScheduler {

	private static final Logger log = LoggerFactory.getLogger(MessageTimerScheduler.class);

	private final MessageRepository messageRepository;
	private final MessageTimerService messageTimerService;
	private final MessagingProperties properties;
	private final Clock clock;

	public MessageTimerScheduler(MessageRepository messageRepository, MessageTimerService messageTimerService,
			MessagingProperties properties, Clock clock) {
		this.messageRepository = messageRepository;
		this.messageTimerService = messageTimerService;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${sonulim.messaging.timer-sweep-interval-ms}")
	public void sweep() {
		LocalDateTime now = LocalDateTime.now(clock);

		// ack 판정 — 늦게 ack가 오면 다시 DELIVERED가 되므로 여기서 끝이 아니다.
		judge("ack 판정", messageRepository
				.findByDeliveryStatusAndAckDeadlineLessThanEqual(DeliveryStatus.REQUESTED, now),
				Message::markUnconfirmed, now);

		// 응답 없음 판정.
		judge("응답 없음 판정", messageRepository
				.findByResponseStatusAndWaitUntilLessThanEqual(ResponseStatus.WAITING, now),
				Message::markNoResponse, now);

		// 긴급 신호 경고 — 전달 후 일정 시간 무응답.
		LocalDateTime warnIfDeliveredBefore = now.minus(properties.emergencyWarningDelay());
		judge("긴급 경고", messageRepository
				.findByEmergencyTrueAndEmergencyWarningFalseAndResponseStatusAndDeliveredAtLessThanEqual(
						ResponseStatus.WAITING, warnIfDeliveredBefore),
				Message::markEmergencyWarning, now);
	}

	private void judge(String what, List<Message> due, BiConsumer<Message, LocalDateTime> verdict, LocalDateTime now) {
		if (due.isEmpty()) {
			return;
		}
		int changed = 0;
		for (Message message : due) {
			if (messageTimerService.apply(message.getId(), verdict, now)) {
				changed++;
			}
		}
		log.debug("{}: 대상 {}건 중 {}건 처리", what, due.size(), changed);
	}
}
