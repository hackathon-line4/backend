package com.sonulim.domain.signal.application;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.messaging.api.MessageResponse;
import com.sonulim.domain.messaging.application.MessageQueryService;
import com.sonulim.domain.messaging.application.SendMessageService;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 신호 알려주기(연습, M-4). 실제 메시지 생성·푸시는 messaging의 {@link SendMessageService}가 맡는다
 * (messages 테이블·MessageKind는 messaging 소유) — messaging이 반대 방향으로 signal의
 * SignalQueryService를 직접 호출하는 것과 같은 패턴이다.
 */
@Service
public class SignalPracticeService {

	private final SendMessageService sendMessageService;
	private final SignalRepository signalRepository;

	public SignalPracticeService(SendMessageService sendMessageService, SignalRepository signalRepository) {
		this.sendMessageService = sendMessageService;
		this.signalRepository = signalRepository;
	}

	public MessageResponse practice(AuthenticatedUser user, UUID linkId, UUID signalId) {
		MessageQueryService.MessageDetail detail = sendMessageService.practice(user, linkId, signalId);
		return MessageResponse.of(detail.message(), detail.sender(), detail.receiver());
	}

	/**
	 * 당사자 ack 때 messaging이 부른다(연습 완료 시점, 이슈 #60에서 확정). 신호가 지워졌어도
	 * ack 자체는 실패하면 안 되므로 조용히 무시한다.
	 */
	@Transactional
	public void markPracticed(UUID signalId, LocalDateTime now) {
		signalRepository.findById(signalId).ifPresent(signal -> signal.markPracticed(now));
	}
}
