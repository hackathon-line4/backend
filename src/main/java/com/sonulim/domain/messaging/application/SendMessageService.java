package com.sonulim.domain.messaging.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.application.UserQueryService;
import com.sonulim.domain.account.application.UserSummary;
import com.sonulim.domain.messaging.domain.DeliveryFailureReason;
import com.sonulim.domain.messaging.domain.Message;
import com.sonulim.domain.messaging.domain.MessageKind;
import com.sonulim.domain.messaging.domain.MessageRepository;
import com.sonulim.domain.messaging.domain.SignalSnapshot;
import com.sonulim.domain.signal.application.SignalQueryService;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.linkaccess.LinkAccessGuard;
import com.sonulim.global.linkaccess.LinkMembers;
import com.sonulim.global.push.PushMessage;
import com.sonulim.global.push.PushSender;
import com.sonulim.global.push.PushType;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 보호자가 신호를 보낸다(S-1, S-2)와 같은 신호로 다시 보낸다(H-1, H-2, S-3).
 *
 * <p>처리 순서는 기록 생성 → 푸시 → ack 타이머다. <b>전달 완료는 당사자 앱의 ack로만</b> 바뀌므로
 * 푸시 발송이 성공해도 상태를 올리지 않는다(공통 규칙).
 */
@Service
@EnableConfigurationProperties(MessagingProperties.class)
public class SendMessageService {

	private final MessageRepository messageRepository;
	private final SignalQueryService signalQueryService;
	private final UserQueryService userQueryService;
	private final LinkAccessGuard linkAccessGuard;
	private final PushSender pushSender;
	private final MessagingEvents messagingEvents;
	private final MessagingProperties properties;
	private final Clock clock;

	public SendMessageService(MessageRepository messageRepository, SignalQueryService signalQueryService,
			UserQueryService userQueryService, LinkAccessGuard linkAccessGuard, PushSender pushSender,
			MessagingEvents messagingEvents, MessagingProperties properties, Clock clock) {
		this.messageRepository = messageRepository;
		this.signalQueryService = signalQueryService;
		this.userQueryService = userQueryService;
		this.linkAccessGuard = linkAccessGuard;
		this.pushSender = pushSender;
		this.messagingEvents = messagingEvents;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public MessageQueryService.MessageDetail send(AuthenticatedUser user, UUID linkId, UUID signalId) {
		MessagingRoles.requireGuardian(user);
		LinkMembers members = linkAccessGuard.requireMembers(linkId, user.userId());
		SignalSnapshot snapshot = SignalSnapshots.of(
				signalQueryService.getDetail(linkId, signalId, user.userId(), user.role()));
		return deliver(Message.send(members.linkId(), members.guardianId(), members.recipientId(),
				MessageKind.SIGNAL, snapshot, LocalDateTime.now(clock), ackTimeout()));
	}

	/**
	 * 같은 신호로 새 기록을 만든다. 스냅샷은 신호의 <b>현재 값</b>으로 다시 뜨고 원래 기록은 건드리지 않는다.
	 * 원래 신호가 지워졌으면 다시 보낼 수 없다.
	 */
	@Transactional
	public MessageQueryService.MessageDetail resend(AuthenticatedUser user, UUID messageId) {
		MessagingRoles.requireGuardian(user);
		Message original = messageRepository.findById(messageId)
				.filter(found -> found.getGuardianId().equals(user.userId()))
				.orElseThrow(() -> new SonulimException(ErrorCode.NOT_FOUND, "기록을 찾을 수 없어요"));
		linkAccessGuard.requireMembers(original.getLinkId(), user.userId());
		SignalSnapshot current = currentSnapshot(user, original);
		return deliver(Message.resend(original, current, LocalDateTime.now(clock), ackTimeout()));
	}

	/**
	 * 기록을 먼저 저장하고 푸시를 보낸다. 당사자 기기가 없거나 발송이 실패해도 기록은 남고 실패 사유만 적힌다 —
	 * 보호자 화면이 '전송 실패'를 보여 주고 다시 보낼 수 있어야 하기 때문이다(신호 보내기 API: 응답은 201).
	 */
	private MessageQueryService.MessageDetail deliver(Message message) {
		messageRepository.save(message);
		try {
			pushSender.send(PushMessage.to(message.getRecipientId(), PushType.SIGNAL)
					.with("messageId", message.getId().toString())
					.with("linkId", message.getLinkId().toString())
					.build());
		}
		catch (RuntimeException pushFailed) {
			message.markFailed(DeliveryFailureReason.PUSH_PROVIDER_ERROR, LocalDateTime.now(clock));
		}
		// 푸시가 실패해도 발행한다 — 앱이 열려 있으면 SSE로 먼저 닿는다.
		UserSummary guardian = messagingEvents.created(message);
		return new MessageQueryService.MessageDetail(message, guardian, user(message.getRecipientId()), true);
	}

	private SignalSnapshot currentSnapshot(AuthenticatedUser user, Message original) {
		UUID signalId = original.getSignalSnapshot().signalId();
		try {
			return SignalSnapshots.of(
					signalQueryService.getDetail(original.getLinkId(), signalId, user.userId(), user.role()));
		}
		catch (SonulimException gone) {
			throw new SonulimException(ErrorCode.SIGNAL_DELETED, "삭제된 신호라 다시 보낼 수 없어요");
		}
	}

	private Duration ackTimeout() {
		return properties.ackTimeout();
	}

	private UserSummary user(UUID userId) {
		return userQueryService.find(userId).orElse(null);
	}
}
