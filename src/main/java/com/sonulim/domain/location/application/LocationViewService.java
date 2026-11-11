package com.sonulim.domain.location.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.location.domain.LastLocation;
import com.sonulim.domain.location.domain.LastLocationRepository;
import com.sonulim.domain.location.domain.LocationRequest;
import com.sonulim.domain.location.domain.LocationRequestRepository;
import com.sonulim.domain.location.domain.LocationRequestStatus;
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
 * 보호자가 보는 위치 — 새로고침 요청(P-1)과 마지막 위치 조회(P-1).
 *
 * <p>이 서비스는 당사자의 위치 공유 상태를 항상 먼저 본다. 꺼 둔 위치는 요청도 조회도 되지 않아야 한다.
 */
@Service
@EnableConfigurationProperties(LocationProperties.class)
public class LocationViewService {

	private static final String GUARDIAN = "GUARDIAN";

	private final LocationRequestRepository locationRequestRepository;
	private final LastLocationRepository lastLocationRepository;
	private final LocationSharingService locationSharingService;
	private final LinkAccessGuard linkAccessGuard;
	private final PushSender pushSender;
	private final LocationEvents locationEvents;
	private final LocationProperties properties;
	private final Clock clock;

	public LocationViewService(LocationRequestRepository locationRequestRepository,
			LastLocationRepository lastLocationRepository, LocationSharingService locationSharingService,
			LinkAccessGuard linkAccessGuard, PushSender pushSender, LocationEvents locationEvents,
			LocationProperties properties, Clock clock) {
		this.locationRequestRepository = locationRequestRepository;
		this.lastLocationRepository = lastLocationRepository;
		this.locationSharingService = locationSharingService;
		this.linkAccessGuard = linkAccessGuard;
		this.pushSender = pushSender;
		this.locationEvents = locationEvents;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * 당사자 휴대폰에 현재 위치를 1회 요청한다.
	 *
	 * <p>진행 중인 요청이 있으면 새로 만들지 않고 그것을 돌려준다 — 버튼을 여러 번 눌러도 당사자 기기가
	 * 여러 번 깨지 않게 하기 위해서다(위치 새로고침 요청 API).
	 */
	@Transactional
	public LocationRequest refresh(AuthenticatedUser user, UUID linkId) {
		requireGuardian(user);
		LinkMembers members = linkAccessGuard.requireMembers(linkId, user.userId());
		requireSharingOn(members.recipientId());
		LocationRequest pending = latestPending(members.linkId());
		if (pending != null && isWaiting(pending)) {
			return pending;
		}
		if (pending != null) {
			// 기한이 지난 요청은 여기서 끝내 둔다 — 스윕을 기다리지 않고 바로 새 요청을 만들 수 있다.
			pending.markTimedOut();
		}
		LocationRequest request = locationRequestRepository.save(LocationRequest.open(members.linkId(),
				members.guardianId(), members.recipientId(), LocalDateTime.now(clock), properties.requestTimeout()));
		pushSender.send(PushMessage.to(members.recipientId(), PushType.LOCATION_REQUEST)
				.with("locationRequestId", request.getId().toString())
				.with("linkId", members.linkId().toString())
				.build());
		locationEvents.requestCreated(request);
		return request;
	}

	/**
	 * 마지막 위치 1건과 공유 상태. 공유가 꺼져 있으면 위치는 돌려주지 않는다 —
	 * 저장된 값이 남아 있더라도 당사자가 끈 뒤에는 보이면 안 된다.
	 */
	@Transactional(readOnly = true)
	public LocationView view(AuthenticatedUser user, UUID linkId) {
		requireGuardian(user);
		LinkMembers members = linkAccessGuard.requireMembers(linkId, user.userId());
		boolean sharing = locationSharingService.isSharingEnabled(members.recipientId());
		LastLocation last = sharing
				? lastLocationRepository.findById(members.recipientId()).orElse(null)
				: null;
		return new LocationView(sharing, last, waitingRequest(members.linkId()));
	}

	/**
	 * 아직 기다리는 중인 요청만. 기한이 지난 것은 보여 주지 않는다 — 보호자 화면이 '요청 중'에 머물면 안 된다.
	 *
	 * <p>여기서 상태를 바꾸지는 않는다. 조회는 읽기 전용이고, 전이는 새로고침 요청과 스윕이 맡는다.
	 */
	private LocationRequest waitingRequest(UUID linkId) {
		LocationRequest pending = latestPending(linkId);
		return pending != null && isWaiting(pending) ? pending : null;
	}

	private LocationRequest latestPending(UUID linkId) {
		return locationRequestRepository
				.findFirstByLinkIdAndStatusOrderByRequestedAtDesc(linkId, LocationRequestStatus.PENDING)
				.orElse(null);
	}

	private boolean isWaiting(LocationRequest request) {
		return request.getTimeoutAt().isAfter(LocalDateTime.now(clock));
	}

	private void requireSharingOn(UUID recipientId) {
		if (!locationSharingService.isSharingEnabled(recipientId)) {
			throw new SonulimException(ErrorCode.LOCATION_SHARING_OFF, "당사자가 위치 공유를 꺼 두었어요");
		}
	}

	private void requireGuardian(AuthenticatedUser user) {
		if (user.role() == null) {
			throw new SonulimException(ErrorCode.ROLE_REQUIRED, "역할을 먼저 선택해 주세요");
		}
		if (!GUARDIAN.equals(user.role())) {
			throw new SonulimException(ErrorCode.ROLE_MISMATCH, "보호자만 사용할 수 있어요");
		}
	}

	public record LocationView(boolean sharingEnabled, LastLocation last, LocationRequest pendingRequest) {
	}
}
