package com.sonulim.domain.location.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.location.domain.LocationRequest;
import com.sonulim.domain.location.domain.LocationRequestRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 위치 새로고침 요청 조회. SSE가 끊겼을 때 보호자 앱이 폴링으로 쓴다.
 */
@Service
public class LocationRequestService {

	private static final String GUARDIAN = "GUARDIAN";

	private final LocationRequestRepository locationRequestRepository;
	private final Clock clock;

	public LocationRequestService(LocationRequestRepository locationRequestRepository, Clock clock) {
		this.locationRequestRepository = locationRequestRepository;
		this.clock = clock;
	}

	/**
	 * 요청 하나의 상태를 돌려준다.
	 *
	 * <p>제한 시간이 지났으면 <b>조회 시점에</b> TIMED_OUT으로 넘긴다(새로고침 요청 상태 API) —
	 * 스윕이 아직 돌지 않았어도 보호자 화면이 '요청 중'에 머무르지 않게 하기 위해서다.
	 */
	@Transactional
	public LocationRequest get(AuthenticatedUser user, UUID requestId) {
		requireGuardian(user);
		LocationRequest request = locationRequestRepository.findById(requestId)
				.filter(found -> found.getGuardianId().equals(user.userId()))
				.orElseThrow(() -> new SonulimException(ErrorCode.NOT_FOUND, "요청을 찾을 수 없어요"));
		if (request.isPending() && !request.getTimeoutAt().isAfter(LocalDateTime.now(clock))) {
			request.markTimedOut();
		}
		return request;
	}

	// TODO messaging·recipient의 같은 검사와 합칠 것. 지금 global로 올리면 브랜치 여럿이 같은 파일을 건드린다.
	private void requireGuardian(AuthenticatedUser user) {
		if (user.role() == null) {
			throw new SonulimException(ErrorCode.ROLE_REQUIRED, "역할을 먼저 선택해 주세요");
		}
		if (!GUARDIAN.equals(user.role())) {
			throw new SonulimException(ErrorCode.ROLE_MISMATCH, "보호자만 사용할 수 있어요");
		}
	}
}
