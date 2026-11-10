package com.sonulim.domain.recipient.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.application.UserQueryService;
import com.sonulim.domain.account.application.UserSummary;
import com.sonulim.domain.recipient.domain.CapturedLocation;
import com.sonulim.domain.recipient.domain.HelpRequest;
import com.sonulim.domain.recipient.domain.HelpRequestRepository;
import com.sonulim.domain.recipient.domain.LocationStatus;
import com.sonulim.domain.recipient.domain.RecipientSignal;
import com.sonulim.domain.recipient.domain.RecipientSignalRepository;
import com.sonulim.domain.recipient.domain.RecipientSignalType;
import com.sonulim.domain.settings.application.LocationSharingService;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.linkaccess.LinkAccessGuard;
import com.sonulim.global.linkaccess.LinkMembers;
import com.sonulim.global.push.PushMessage;
import com.sonulim.global.push.PushSender;
import com.sonulim.global.push.PushType;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 당사자가 보내는 두 가지 — 보호자 부르기(R-2)와 도움 요청(R-3).
 *
 * <p>당사자 화면은 서버가 확인해 준 뒤에만 완료 상태를 보여 주므로, 기록을 먼저 저장하고 푸시를 보낸다.
 */
@Service
public class RecipientSendService {

	private static final String RECIPIENT = "RECIPIENT";

	private final RecipientSignalRepository recipientSignalRepository;
	private final HelpRequestRepository helpRequestRepository;
	private final LocationSharingService locationSharingService;
	private final UserQueryService userQueryService;
	private final LinkAccessGuard linkAccessGuard;
	private final PushSender pushSender;
	private final Clock clock;

	public RecipientSendService(RecipientSignalRepository recipientSignalRepository,
			HelpRequestRepository helpRequestRepository, LocationSharingService locationSharingService,
			UserQueryService userQueryService, LinkAccessGuard linkAccessGuard, PushSender pushSender, Clock clock) {
		this.recipientSignalRepository = recipientSignalRepository;
		this.helpRequestRepository = helpRequestRepository;
		this.locationSharingService = locationSharingService;
		this.userQueryService = userQueryService;
		this.linkAccessGuard = linkAccessGuard;
		this.pushSender = pushSender;
		this.clock = clock;
	}

	/**
	 * 보호자를 부른다. 보낸 시각은 서버가 받은 시각으로 기록한다 — 본문의 값은 참고용이다.
	 */
	@Transactional
	public CalledGuardian callGuardian(AuthenticatedUser user, UUID linkId, RecipientSignalType type) {
		requireRecipient(user);
		LinkMembers members = linkAccessGuard.requireMembers(linkId, user.userId());
		RecipientSignal signal = recipientSignalRepository.save(RecipientSignal.send(members.linkId(),
				user.userId(), members.guardianId(), type, LocalDateTime.now(clock)));
		pushSender.send(PushMessage.to(members.guardianId(), PushType.RECIPIENT_SIGNAL)
				.with("recipientSignalId", signal.getId().toString())
				.with("linkId", members.linkId().toString())
				.build());
		return new CalledGuardian(signal, user(user.userId()));
	}

	/**
	 * 도움 요청을 보낸다. 보호자에게는 알림 설정과 무관하게 항상 전달된다(서버 내부 처리: 끌 수 없음).
	 *
	 * <p>위치 공유가 꺼져 있으면 좌표가 와도 저장하지 않고 NOT_SHARED로만 남긴다 —
	 * 당사자가 끈 위치가 기록에 들어가면 안 된다.
	 */
	@Transactional
	public HelpRequestQueryService.HelpRequestDetail requestHelp(AuthenticatedUser user, UUID linkId,
			LocationStatus locationStatus, CapturedLocation location) {
		requireRecipient(user);
		LinkMembers members = linkAccessGuard.requireMembers(linkId, user.userId());
		LocationStatus stored = withSharingApplied(user, locationStatus);
		HelpRequest request = helpRequestRepository.save(HelpRequest.send(members.linkId(), user.userId(),
				members.guardianId(), LocalDateTime.now(clock), stored, location));
		pushSender.send(PushMessage.to(members.guardianId(), PushType.HELP_REQUEST)
				.with("helpRequestId", request.getId().toString())
				.with("linkId", members.linkId().toString())
				.build());
		return new HelpRequestQueryService.HelpRequestDetail(request, user(user.userId()), java.util.List.of());
	}

	/** 공유가 꺼져 있으면 기기가 뭘 보냈든 NOT_SHARED다. HelpRequest가 그때 좌표를 버린다. */
	private LocationStatus withSharingApplied(AuthenticatedUser user, LocationStatus requested) {
		return locationSharingService.get(user).isEnabled() ? requested : LocationStatus.NOT_SHARED;
	}

	private UserSummary user(UUID userId) {
		return userQueryService.find(userId).orElse(null);
	}

	private void requireRecipient(AuthenticatedUser user) {
		if (user.role() == null) {
			throw new SonulimException(ErrorCode.ROLE_REQUIRED, "역할을 먼저 선택해 주세요");
		}
		if (!RECIPIENT.equals(user.role())) {
			throw new SonulimException(ErrorCode.ROLE_MISMATCH, "당사자만 사용할 수 있어요");
		}
	}

	public record CalledGuardian(RecipientSignal signal, UserSummary sender) {
	}
}
