package com.sonulim.domain.messaging.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.messaging.domain.Message;
import com.sonulim.domain.messaging.domain.MessageRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * '더 기다리기'(S-3) — 응답 없음으로 끝난 기록의 대기 시간을 늘려 늦은 응답을 받는다.
 */
@Service
@EnableConfigurationProperties(MessagingProperties.class)
public class MessageWaitService {

	private final MessageRepository messageRepository;
	private final MessagingEvents messagingEvents;
	private final MessagingProperties properties;
	private final Clock clock;

	public MessageWaitService(MessageRepository messageRepository, MessagingEvents messagingEvents,
			MessagingProperties properties, Clock clock) {
		this.messageRepository = messageRepository;
		this.messagingEvents = messagingEvents;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * @param minutes 생략하면 보호자 설정값. settings 도메인 전까지는 서버 기본값을 쓴다
	 */
	@Transactional
	public MessageQueryService.MessageDetail extendWait(AuthenticatedUser user, UUID messageId, Integer minutes) {
		MessagingRoles.requireGuardian(user);
		Message message = messageRepository.findById(messageId)
				.filter(found -> found.getGuardianId().equals(user.userId()))
				.orElseThrow(() -> new SonulimException(ErrorCode.NOT_FOUND, "기록을 찾을 수 없어요"));
		message.extendWait(LocalDateTime.now(clock),
				minutes != null ? minutes : properties.defaultResponseWaitMinutes());
		MessagingEvents.Participants participants = messagingEvents.updated(message);
		// 더 기다리기 응답에는 타임라인이 없어 canResend를 쓰지 않는다.
		return new MessageQueryService.MessageDetail(message, participants.guardian(), participants.recipient(),
				false);
	}
}
