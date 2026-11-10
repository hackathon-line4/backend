package com.sonulim.domain.messaging.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.messaging.domain.Message;
import com.sonulim.domain.messaging.domain.MessageRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 당사자 기기가 보내는 수신 보고 — 수신 확인(ack), 진동 재생 보고, 터치 응답.
 *
 * <p>당사자 화면은 서버가 확인해 준 뒤에만 완료 상태를 보여 주므로, 세 가지 모두 여러 번 와도 처음 값만 기록하고
 * 같은 결과를 돌려준다(공통 규칙 · 각 API 페이지).
 */
@Service
public class MessageReceiptService {

	private final MessageRepository messageRepository;
	private final MessagingProperties properties;
	private final Clock clock;

	public MessageReceiptService(MessageRepository messageRepository, MessagingProperties properties, Clock clock) {
		this.messageRepository = messageRepository;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public Message ack(AuthenticatedUser user, UUID messageId, LocalDateTime deviceReceivedAt) {
		Message message = findForRecipient(user, messageId);
		message.ack(LocalDateTime.now(clock), deviceReceivedAt, properties.defaultResponseWaitMinutes());
		return message;
	}

	@Transactional
	public void reportPlayback(AuthenticatedUser user, UUID messageId, int count) {
		findForRecipient(user, messageId).reportPlayback(count, LocalDateTime.now(clock));
	}

	@Transactional
	public Message respond(AuthenticatedUser user, UUID messageId) {
		Message message = findForRecipient(user, messageId);
		message.respond(LocalDateTime.now(clock));
		return message;
	}

	/**
	 * 받는 사람 본인의 기록만 꺼낸다.
	 *
	 * <p>역할이 다르면 403, 내 기록이 아니면 <b>404</b>다 — 남의 기록이 존재한다는 사실 자체를 숨긴다(공통 규칙의 연결 범위).
	 */
	private Message findForRecipient(AuthenticatedUser user, UUID messageId) {
		MessagingRoles.requireRecipient(user);
		return messageRepository.findById(messageId)
				.filter(message -> message.getRecipientId().equals(user.userId()))
				.orElseThrow(() -> new SonulimException(ErrorCode.NOT_FOUND, "기록을 찾을 수 없어요"));
	}

}
