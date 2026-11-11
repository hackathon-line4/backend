package com.sonulim.domain.recipient.application;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sonulim.domain.account.application.UserQueryService;
import com.sonulim.domain.account.application.UserSummary;
import com.sonulim.domain.recipient.api.HelpRequestResponse;
import com.sonulim.domain.recipient.api.RecipientSignalResponse;
import com.sonulim.domain.recipient.domain.HelpRequest;
import com.sonulim.domain.recipient.domain.RecipientSignal;
import com.sonulim.global.realtime.RealtimeEventPublisher;
import com.sonulim.global.realtime.RealtimeEventType;

/**
 * recipient의 SSE 이벤트 발행(docs/realtime.md). 받는 사람과 data 모양은 API 명세의 이벤트 명세 표를 따른다.
 *
 * <p>data는 같은 상태를 보여 주는 API 응답과 같은 모양이어야 해서 응답 DTO를 그대로 넘긴다.
 */
@Component
public class RecipientEvents {

	private final RealtimeEventPublisher realtimeEvents;
	private final UserQueryService userQueryService;

	public RecipientEvents(RealtimeEventPublisher realtimeEvents, UserQueryService userQueryService) {
		this.realtimeEvents = realtimeEvents;
		this.userQueryService = userQueryService;
	}

	/** 당사자가 보호자를 불렀다 → <b>보호자</b>에게. */
	public void signalCreated(RecipientSignal signal) {
		realtimeEvents.publish(signal.getGuardianId(), RealtimeEventType.RECIPIENT_SIGNAL_CREATED,
				Map.of("recipientSignal", RecipientSignalResponse.of(signal, user(signal.getSenderId()))));
	}

	/** 도움 요청이 왔다 → <b>보호자</b>에게. */
	public void helpRequestCreated(HelpRequest request) {
		realtimeEvents.publish(request.getGuardianId(), RealtimeEventType.HELP_REQUEST_CREATED,
				Map.of("helpRequest", HelpRequestResponse.of(request, user(request.getSenderId()))));
	}

	/**
	 * 위치가 붙었거나 보호자가 열어 봤다 → <b>보호자와 당사자 둘 다</b>에게.
	 * 당사자 앱은 이 이벤트로 '확인함'을 알고, 보호자 화면은 붙은 위치를 받는다.
	 */
	public void helpRequestUpdated(HelpRequest request) {
		Object data = Map.of("helpRequest", HelpRequestResponse.of(request, user(request.getSenderId())));
		realtimeEvents.publish(request.getGuardianId(), RealtimeEventType.HELP_REQUEST_UPDATED, data);
		realtimeEvents.publish(request.getSenderId(), RealtimeEventType.HELP_REQUEST_UPDATED, data);
	}

	private UserSummary user(UUID userId) {
		return userQueryService.find(userId).orElse(null);
	}
}
