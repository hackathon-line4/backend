package com.sonulim.domain.location.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.location.domain.LocationRequest;
import com.sonulim.domain.location.domain.LocationRequestRepository;
import com.sonulim.domain.location.domain.LocationRequestStatus;

/**
 * 제한 시간 안에 위치가 오지 않은 요청을 시간 초과로 넘긴다.
 *
 * <p>조회 API도 같은 판정을 하지만(폴링하는 보호자를 기다리게 하지 않으려고), 아무도 조회하지 않는 요청은
 * 여기서 정리된다. 기한을 DB 컬럼으로 들고 있어 서버가 재시작해도 남는다.
 *
 * <p>ponytail: 짧은 주기 폴링이다. 요청 수가 적어 부담이 없고, 늘어나면 messaging 타이머와 함께 지연 큐로 바꾼다.
 */
@Component
public class LocationRequestTimeoutScheduler {

	private static final Logger log = LoggerFactory.getLogger(LocationRequestTimeoutScheduler.class);

	private final LocationRequestRepository locationRequestRepository;
	private final LocationEvents locationEvents;
	private final Clock clock;

	public LocationRequestTimeoutScheduler(LocationRequestRepository locationRequestRepository,
			LocationEvents locationEvents, Clock clock) {
		this.locationRequestRepository = locationRequestRepository;
		this.locationEvents = locationEvents;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${sonulim.location.timeout-sweep-interval-ms}")
	@Transactional
	public void sweep() {
		List<LocationRequest> due = locationRequestRepository
				.findByStatusAndTimeoutAtLessThanEqual(LocationRequestStatus.PENDING, LocalDateTime.now(clock));
		if (due.isEmpty()) {
			return;
		}
		// 늦게 도착한 위치가 먼저 반영됐다면 markTimedOut이 스스로 건너뛴다 — 그런 건은 알리지 않는다.
		int changed = 0;
		for (LocationRequest request : due) {
			if (request.markTimedOut()) {
				locationEvents.requestUpdated(request);
				changed++;
			}
		}
		log.debug("위치 요청 시간 초과: 대상 {}건 중 {}건 처리", due.size(), changed);
	}
}
