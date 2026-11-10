package com.sonulim.domain.messaging.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.application.UserQueryService;
import com.sonulim.domain.account.application.UserSummary;
import com.sonulim.domain.messaging.domain.Message;
import com.sonulim.domain.signal.application.SignalQueryService;
import com.sonulim.domain.messaging.domain.MessageRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 보낸 신호 조회와 기록 상세. 응답에 상대방 이름이 들어가므로 account의 읽기 전용 조회를 쓴다.
 */
@Service
@EnableConfigurationProperties(MessagingProperties.class)
public class MessageQueryService {

	private final MessageRepository messageRepository;
	private final UserQueryService userQueryService;
	private final SignalQueryService signalQueryService;
	private final MessagingProperties properties;
	private final Clock clock;

	public MessageQueryService(MessageRepository messageRepository, UserQueryService userQueryService,
			SignalQueryService signalQueryService, MessagingProperties properties, Clock clock) {
		this.messageRepository = messageRepository;
		this.userQueryService = userQueryService;
		this.signalQueryService = signalQueryService;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * 당사자가 지금 울려야 하는 신호. 앱 시작·포그라운드 복귀·SSE 재연결 때 부른다.
	 *
	 * <p>보호자 이름은 한 번에 모아 읽는다 — 항목마다 조회하면 목록 길이만큼 쿼리가 늘어난다.
	 */
	@Transactional(readOnly = true)
	public IncomingMessages incoming(AuthenticatedUser user) {
		MessagingRoles.requireRecipient(user);
		List<Message> messages = messageRepository.findIncoming(user.userId());
		Set<UUID> guardianIds = messages.stream().map(Message::getGuardianId).collect(Collectors.toSet());
		return new IncomingMessages(messages, userQueryService.findAll(guardianIds),
				properties.emergencyPlayback());
	}

	/**
	 * 기록 상세. 보낸 사람인 보호자만 볼 수 있고, 내 기록이 아니면 404다(존재를 숨긴다).
	 */
	@Transactional(readOnly = true)
	public MessageDetail detail(AuthenticatedUser user, UUID messageId) {
		MessagingRoles.requireGuardian(user);
		Message message = messageRepository.findWithTimeline(messageId)
				.filter(found -> found.getGuardianId().equals(user.userId()))
				.orElseThrow(() -> new SonulimException(ErrorCode.NOT_FOUND, "기록을 찾을 수 없어요"));
		return new MessageDetail(message, user(message.getGuardianId()), user(message.getRecipientId()),
				signalStillExists(user, message));
	}

	/**
	 * 다시 보내기는 신호의 현재 값으로 새 스냅샷을 뜨므로, 원래 신호가 지워졌으면 할 수 없다.
	 *
	 * <p>signal 도메인은 조회 실패를 예외로 알려 준다 — 삭제 여부만 필요해서 그 예외를 false로 받는다.
	 */
	private boolean signalStillExists(AuthenticatedUser user, Message message) {
		UUID signalId = message.getSignalSnapshot().signalId();
		if (signalId == null) {
			return false;
		}
		try {
			signalQueryService.getDetail(message.getLinkId(), signalId, user.userId(), user.role());
			return true;
		}
		catch (SonulimException deletedOrGone) {
			return false;
		}
	}

	LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private UserSummary user(UUID userId) {
		return userQueryService.find(userId).orElse(null);
	}

	/** 목록과 함께 이름·재생 정책을 넘겨, 컨트롤러가 추가 조회 없이 응답을 만들 수 있게 한다. */
	public record IncomingMessages(List<Message> messages, Map<UUID, UserSummary> guardians,
			MessagingProperties.EmergencyPlayback emergencyPlayback) {
	}

	public record MessageDetail(Message message, UserSummary sender, UserSummary receiver, boolean canResend) {
	}
}
