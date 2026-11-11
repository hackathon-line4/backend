package com.sonulim.domain.location.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.location.domain.LastLocation;
import com.sonulim.domain.location.domain.LastLocationRepository;
import com.sonulim.domain.location.domain.LocationFailureReason;
import com.sonulim.domain.location.domain.LocationRequest;
import com.sonulim.domain.location.domain.LocationRequestRepository;
import com.sonulim.domain.settings.application.LocationSharingService;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 당사자 기기가 보낸 위치를 받는다(P-1). 사용자 조작 없이 백그라운드에서 호출된다.
 */
@Service
public class FulfillLocationService {

	private static final String RECIPIENT = "RECIPIENT";

	private final LocationRequestRepository locationRequestRepository;
	private final LastLocationRepository lastLocationRepository;
	private final LocationSharingService locationSharingService;
	private final LocationEvents locationEvents;

	public FulfillLocationService(LocationRequestRepository locationRequestRepository,
			LastLocationRepository lastLocationRepository, LocationSharingService locationSharingService,
			LocationEvents locationEvents) {
		this.locationRequestRepository = locationRequestRepository;
		this.lastLocationRepository = lastLocationRepository;
		this.locationSharingService = locationSharingService;
		this.locationEvents = locationEvents;
	}

	/**
	 * 위치를 받아 마지막 위치를 덮어쓰고 요청을 끝낸다.
	 *
	 * <p>시간 초과로 이미 끝난 요청이어도 <b>위치 자체는 마지막 위치에 반영한다</b> — 보호자 화면은 '실패'를
	 * 보여 줬지만, 늦게라도 도착한 위치를 버릴 이유가 없다(위치 보내기 API).
	 *
	 * <p>공유가 꺼져 있으면 좌표가 와도 저장하지 않는다. 당사자가 끈 위치가 남으면 안 된다.
	 */
	@Transactional
	public void fulfill(AuthenticatedUser user, UUID requestId, BigDecimal latitude, BigDecimal longitude,
			Integer accuracyM, LocalDateTime capturedAt) {
		LocationRequest request = findOwn(user, requestId);
		if (!locationSharingService.isSharingEnabled(user.userId())) {
			request.markFailed(LocationFailureReason.SHARING_OFF);
			locationEvents.requestUpdated(request);
			return;
		}
		LastLocation last = overwriteLastLocation(user.userId(), latitude, longitude, accuracyM, capturedAt);
		request.markFulfilled();
		// 시간 초과로 이미 끝난 요청이어도 보낸다 — 늦게 도착한 위치도 보호자에게는 새 위치다.
		locationEvents.locationUpdated(request, last);
	}

	/** 기기가 위치를 얻지 못했다. 마지막 위치는 그대로 둔다 — 예전 위치가 지워질 이유가 없다. */
	@Transactional
	public void fail(AuthenticatedUser user, UUID requestId, LocationFailureReason reason) {
		LocationRequest request = findOwn(user, requestId);
		request.markFailed(reason);
		locationEvents.requestUpdated(request);
	}

	/**
	 * 당사자당 한 건만 두고 덮어쓴다 — 이동 경로를 쌓지 않는 것이 이 서비스의 위치 정책이다.
	 * 주소는 좌표를 주소로 바꾸는 외부 연동이 정해지면 채운다.
	 */
	private LastLocation overwriteLastLocation(UUID recipientId, BigDecimal latitude, BigDecimal longitude,
			Integer accuracyM, LocalDateTime capturedAt) {
		return lastLocationRepository.findById(recipientId)
				.map(last -> {
					last.overwriteWith(latitude, longitude, accuracyM, capturedAt);
					return last;
				})
				.orElseGet(() -> lastLocationRepository.save(
						LastLocation.of(recipientId, latitude, longitude, accuracyM, capturedAt)));
	}

	private LocationRequest findOwn(AuthenticatedUser user, UUID requestId) {
		requireRecipient(user);
		return locationRequestRepository.findById(requestId)
				.filter(found -> found.getRecipientId().equals(user.userId()))
				.orElseThrow(() -> new SonulimException(ErrorCode.NOT_FOUND, "요청을 찾을 수 없어요"));
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
