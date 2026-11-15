package com.sonulim.domain.signal.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sonulim.domain.messaging.api.MessageResponse;
import com.sonulim.domain.messaging.application.MessageQueryService;
import com.sonulim.domain.messaging.application.SendMessageService;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 신호 알려주기(연습, M-4). 실제 메시지 생성·푸시는 messaging의 {@link SendMessageService}가 맡는다
 * (messages 테이블·MessageKind는 messaging 소유) — messaging이 반대 방향으로 signal의
 * SignalQueryService를 직접 호출하는 것과 같은 패턴이다.
 */
@Service
public class SignalPracticeService {

	private final SendMessageService sendMessageService;

	public SignalPracticeService(SendMessageService sendMessageService) {
		this.sendMessageService = sendMessageService;
	}

	public MessageResponse practice(AuthenticatedUser user, UUID linkId, UUID signalId) {
		MessageQueryService.MessageDetail detail = sendMessageService.practice(user, linkId, signalId);
		return MessageResponse.of(detail.message(), detail.sender(), detail.receiver());
	}
}
