package com.sonulim.domain.recipient.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.application.UserQueryService;
import com.sonulim.domain.account.application.UserSummary;
import com.sonulim.domain.recipient.domain.CapturedLocation;
import com.sonulim.domain.recipient.domain.HelpRequest;
import com.sonulim.domain.recipient.domain.HelpRequestRepository;
import com.sonulim.domain.recipient.domain.LocationStatus;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 도움 요청에 나중에 얻은 위치를 붙인다(R-3).
 *
 * <p>도움 요청은 위치를 기다리느라 늦어지면 안 되므로 PENDING으로 먼저 보내고, 위치를 얻는 대로 여기서 채운다.
 */
@Service
public class HelpRequestLocationService {

	private static final String RECIPIENT = "RECIPIENT";

	private final HelpRequestRepository helpRequestRepository;
	private final UserQueryService userQueryService;
	private final RecipientEvents recipientEvents;

	public HelpRequestLocationService(HelpRequestRepository helpRequestRepository,
			UserQueryService userQueryService, RecipientEvents recipientEvents) {
		this.helpRequestRepository = helpRequestRepository;
		this.userQueryService = userQueryService;
		this.recipientEvents = recipientEvents;
	}

	/**
	 * 요청을 보낸 당사자 본인만 붙일 수 있다. 남의 요청은 존재를 숨기려고 404로 응답한다.
	 *
	 * @param status ATTACHED, 또는 끝내 못 얻었다면 UNAVAILABLE
	 */
	@Transactional
	public AttachedLocation attach(AuthenticatedUser user, UUID helpRequestId,
			LocationStatus status, CapturedLocation location) {
		requireRecipient(user);
		requireAttachable(status, location);
		HelpRequest request = helpRequestRepository.findById(helpRequestId)
				.filter(found -> found.getSenderId().equals(user.userId()))
				.orElseThrow(() -> new SonulimException(ErrorCode.NOT_FOUND, "도움 요청을 찾을 수 없어요"));
		request.attachLocation(status, location);
		recipientEvents.helpRequestLocationAttached(request);
		// 주소는 좌표를 주소로 바꾸는 외부 연동이 정해지면 채운다 — 그동안은 null로 둔다(명세 허용).
		return new AttachedLocation(request, userQueryService.find(request.getSenderId()).orElse(null));
	}

	public record AttachedLocation(HelpRequest helpRequest, UserSummary sender) {
	}

	private static void requireAttachable(LocationStatus status, CapturedLocation location) {
		if (status != LocationStatus.ATTACHED && status != LocationStatus.UNAVAILABLE) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED,
					"위치 상태는 ATTACHED 또는 UNAVAILABLE이어야 해요", "locationStatus");
		}
		if (status == LocationStatus.ATTACHED && location == null) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED, "위치가 필요해요", "location");
		}
	}

	private void requireRecipient(AuthenticatedUser user) {
		if (user.role() == null) {
			throw new SonulimException(ErrorCode.ROLE_REQUIRED, "역할을 먼저 선택해 주세요");
		}
		if (!RECIPIENT.equals(user.role())) {
			throw new SonulimException(ErrorCode.ROLE_MISMATCH, "당사자만 사용할 수 있어요");
		}
	}
}
