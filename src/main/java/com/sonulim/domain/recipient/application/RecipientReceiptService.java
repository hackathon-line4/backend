package com.sonulim.domain.recipient.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.recipient.domain.HelpRequest;
import com.sonulim.domain.recipient.domain.HelpRequestRepository;
import com.sonulim.domain.recipient.domain.RecipientSignal;
import com.sonulim.domain.recipient.domain.RecipientSignalRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 당사자가 보낸 것을 보호자 앱이 받고 열어 봤다고 보고한다.
 *
 * <p>두 기록 모두 처음 시각만 기록하므로 같은 보고가 여러 번 와도 안전하다 — 푸시 수신 핸들러가 중복 호출될 수 있다.
 */
@Service
public class RecipientReceiptService {

	private static final String GUARDIAN = "GUARDIAN";

	private final RecipientSignalRepository recipientSignalRepository;
	private final HelpRequestRepository helpRequestRepository;
	private final Clock clock;

	public RecipientReceiptService(RecipientSignalRepository recipientSignalRepository,
			HelpRequestRepository helpRequestRepository, Clock clock) {
		this.recipientSignalRepository = recipientSignalRepository;
		this.helpRequestRepository = helpRequestRepository;
		this.clock = clock;
	}

	@Transactional
	public void recordSignalDelivered(AuthenticatedUser user, UUID signalId) {
		findSignal(user, signalId).markDelivered(LocalDateTime.now(clock));
	}

	@Transactional
	public void recordSignalOpened(AuthenticatedUser user, UUID signalId) {
		findSignal(user, signalId).markOpened(LocalDateTime.now(clock));
	}

	@Transactional
	public void recordHelpRequestDelivered(AuthenticatedUser user, UUID helpRequestId) {
		findHelpRequest(user, helpRequestId).markDelivered(LocalDateTime.now(clock));
	}

	/**
	 * 도움 요청 열람. 첫 열람일 때만 재알림을 멈추고 당사자에게 '확인함'을 보낸다 — 두 번 보내면 안 된다.
	 *
	 * @return 이번이 첫 열람이면 true
	 */
	@Transactional
	public boolean recordHelpRequestOpened(AuthenticatedUser user, UUID helpRequestId) {
		boolean firstOpen = findHelpRequest(user, helpRequestId).markOpened(LocalDateTime.now(clock));
		if (firstOpen) {
			// TODO 푸시·실시간 구현 후: 재알림 중단과 당사자 HELP_CONFIRMED 발송을 여기서 건다.
			//      지금은 markOpened가 남긴 열람 시각만으로 재알림 대상에서 빠진다.
		}
		return firstOpen;
	}

	private RecipientSignal findSignal(AuthenticatedUser user, UUID signalId) {
		requireGuardian(user);
		return recipientSignalRepository.findById(signalId)
				.filter(signal -> signal.getGuardianId().equals(user.userId()))
				.orElseThrow(RecipientReceiptService::notFound);
	}

	private HelpRequest findHelpRequest(AuthenticatedUser user, UUID helpRequestId) {
		requireGuardian(user);
		return helpRequestRepository.findById(helpRequestId)
				.filter(request -> request.getGuardianId().equals(user.userId()))
				.orElseThrow(RecipientReceiptService::notFound);
	}

	/** 내 연결의 기록이 아니면 403이 아니라 404다 — 존재 자체를 숨긴다(공통 규칙의 연결 범위). */
	private static SonulimException notFound() {
		return new SonulimException(ErrorCode.NOT_FOUND, "기록을 찾을 수 없어요");
	}

	// TODO messaging의 같은 검사와 합칠 것. 지금 global로 올리면 브랜치 세 개가 같은 파일을 건드린다.
	private void requireGuardian(AuthenticatedUser user) {
		if (user.role() == null) {
			throw new SonulimException(ErrorCode.ROLE_REQUIRED, "역할을 먼저 선택해 주세요");
		}
		if (!GUARDIAN.equals(user.role())) {
			throw new SonulimException(ErrorCode.ROLE_MISMATCH, "보호자만 사용할 수 있어요");
		}
	}
}
