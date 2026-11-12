package com.sonulim.domain.messaging.application;

import java.util.Map;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.sonulim.domain.account.application.UserQueryService;
import com.sonulim.domain.account.application.UserSummary;
import com.sonulim.domain.messaging.api.IncomingMessageResponse;
import com.sonulim.domain.messaging.api.MessageResponse;
import com.sonulim.domain.messaging.domain.Message;
import com.sonulim.global.realtime.RealtimeEventPublisher;
import com.sonulim.global.realtime.RealtimeEventType;

/**
 * messaging의 SSE 이벤트 발행(docs/realtime.md). 받는 사람과 data 모양은 API 명세의 이벤트 명세 표를 따른다.
 *
 * <p>data는 같은 상태를 보여 주는 API 응답과 <b>같은 모양</b>이어야 해서(공통 데이터 형식) 응답 DTO를 그대로 넘긴다 —
 * SSE용으로 모양을 한 벌 더 두면 둘이 어긋난다.
 */
@Component
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingEvents {

	private final RealtimeEventPublisher realtimeEvents;
	private final UserQueryService userQueryService;
	private final MessagingProperties properties;

	public MessagingEvents(RealtimeEventPublisher realtimeEvents, UserQueryService userQueryService,
			MessagingProperties properties) {
		this.realtimeEvents = realtimeEvents;
		this.userQueryService = userQueryService;
		this.properties = properties;
	}

	/**
	 * 보호자가 신호·연습을 보냈다 → <b>당사자</b>에게. 앱이 열려 있으면 푸시를 기다리지 않고 바로 울릴 수 있도록
	 * 받은 신호 조회·SIGNAL 푸시와 같은 모양(IncomingMessage)으로 보낸다.
	 *
	 * @return 조회한 보호자 정보 — 부르는 쪽이 응답을 또 만들 때 같은 조회를 반복하지 않도록 돌려준다
	 */
	public UserSummary created(Message message) {
		UserSummary guardian = user(message.getGuardianId());
		realtimeEvents.publish(message.getRecipientId(), RealtimeEventType.MESSAGE_CREATED,
				Map.of("message", IncomingMessageResponse.of(message, guardian == null ? null : guardian.name(),
						properties.emergencyPlayback())));
		return guardian;
	}

	/**
	 * 기기 전달·재생 보고·응답·응답 없음·대기 연장·긴급 경고 → <b>보호자</b>에게.
	 * 보호자 화면이 기록 하나를 그대로 갈아 끼울 수 있게 기록 전체를 보낸다.
	 *
	 * @return 조회한 보호자·당사자 정보 — 부르는 쪽이 응답을 또 만들 때 같은 조회를 반복하지 않도록 돌려준다
	 */
	public Participants updated(Message message) {
		UserSummary guardian = user(message.getGuardianId());
		UserSummary recipient = user(message.getRecipientId());
		realtimeEvents.publish(message.getGuardianId(), RealtimeEventType.MESSAGE_UPDATED,
				Map.of("message", MessageResponse.of(message, guardian, recipient)));
		return new Participants(guardian, recipient);
	}

	private UserSummary user(UUID userId) {
		return userQueryService.find(userId).orElse(null);
	}

	public record Participants(UserSummary guardian, UserSummary recipient) {
	}
}
