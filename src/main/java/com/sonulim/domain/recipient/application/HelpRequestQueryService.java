package com.sonulim.domain.recipient.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.application.UserQueryService;
import com.sonulim.domain.account.application.UserSummary;
import com.sonulim.domain.messaging.application.MessageQueryService;
import com.sonulim.domain.recipient.domain.HelpRequest;
import com.sonulim.domain.recipient.domain.HelpRequestRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 도움 요청 상세(H-3). 열람은 기록하지 않는다 — 조회와 기록을 나눠 두어 화면이 상세를 다시 열어도 '열어 본 시각'이 밀리지 않는다.
 */
@Service
public class HelpRequestQueryService {

	/** 명세: 요청 이후 보호자가 보낸 신호를 다음 도움 요청 전까지 최대 20건. */
	private static final int FOLLOWING_MESSAGE_LIMIT = 20;

	private static final String GUARDIAN = "GUARDIAN";

	private final HelpRequestRepository helpRequestRepository;
	private final MessageQueryService messageQueryService;
	private final UserQueryService userQueryService;

	public HelpRequestQueryService(HelpRequestRepository helpRequestRepository,
			MessageQueryService messageQueryService, UserQueryService userQueryService) {
		this.helpRequestRepository = helpRequestRepository;
		this.messageQueryService = messageQueryService;
		this.userQueryService = userQueryService;
	}

	@Transactional(readOnly = true)
	public HelpRequestDetail detail(AuthenticatedUser user, UUID helpRequestId) {
		requireGuardian(user);
		HelpRequest request = helpRequestRepository.findById(helpRequestId)
				.filter(found -> found.getGuardianId().equals(user.userId()))
				.orElseThrow(() -> new SonulimException(ErrorCode.NOT_FOUND, "도움 요청을 찾을 수 없어요"));
		return new HelpRequestDetail(request, userQueryService.find(request.getSenderId()).orElse(null),
				followingMessages(request));
	}

	/**
	 * 요청 이후 보낸 신호. 다음 도움 요청이 있으면 거기까지만 — 그 뒤 신호는 다음 요청에 딸린 대화다.
	 */
	private List<MessageQueryService.SentSignal> followingMessages(HelpRequest request) {
		LocalDateTime until = helpRequestRepository
				.findFirstByLinkIdAndSentAtGreaterThanOrderBySentAtAsc(request.getLinkId(), request.getSentAt())
				.map(HelpRequest::getSentAt)
				.orElse(LocalDateTime.now().plusYears(100));
		return messageQueryService.sentBetween(request.getLinkId(), request.getSentAt(), until,
				FOLLOWING_MESSAGE_LIMIT);
	}

	private void requireGuardian(AuthenticatedUser user) {
		if (user.role() == null) {
			throw new SonulimException(ErrorCode.ROLE_REQUIRED, "역할을 먼저 선택해 주세요");
		}
		if (!GUARDIAN.equals(user.role())) {
			throw new SonulimException(ErrorCode.ROLE_MISMATCH, "보호자만 사용할 수 있어요");
		}
	}

	public record HelpRequestDetail(HelpRequest helpRequest, UserSummary sender,
			List<MessageQueryService.SentSignal> followingMessages) {
	}
}
