package com.sonulim.domain.location.application;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.sonulim.domain.location.api.LocationRequestResponse;
import com.sonulim.domain.location.api.LocationViewResponse;
import com.sonulim.domain.location.domain.LastLocation;
import com.sonulim.domain.location.domain.LocationRequest;
import com.sonulim.global.realtime.RealtimeEventPublisher;
import com.sonulim.global.realtime.RealtimeEventType;

/**
 * location의 SSE 이벤트 발행(docs/realtime.md). 받는 사람과 data 모양은 API 명세의 이벤트 명세 표를 따른다.
 *
 * <p>data는 같은 상태를 보여 주는 API 응답과 같은 모양이어야 해서 응답 DTO를 그대로 넘긴다.
 */
@Component
public class LocationEvents {

	private final RealtimeEventPublisher realtimeEvents;

	public LocationEvents(RealtimeEventPublisher realtimeEvents) {
		this.realtimeEvents = realtimeEvents;
	}

	/**
	 * 보호자가 위치를 새로고침했다 → <b>당사자</b>에게. 당사자 앱은 이 id로 위치 보내기 API를 부른다.
	 *
	 * <p>요청 내용은 당사자에게 필요 없으므로 id만 보낸다(명세의 data는 {@code {requestId}}).
	 */
	public void requestCreated(LocationRequest request) {
		realtimeEvents.publish(request.getRecipientId(), RealtimeEventType.LOCATION_REQUEST_CREATED,
				Map.of("requestId", request.getId()));
	}

	/** 새로고침이 실패하거나 시간이 초과됐다 → <b>보호자</b>에게. */
	public void requestUpdated(LocationRequest request) {
		realtimeEvents.publish(request.getGuardianId(), RealtimeEventType.LOCATION_REQUEST_UPDATED,
				Map.of("locationRequest", LocationRequestResponse.from(request)));
	}

	/**
	 * 새 위치가 도착했다 → <b>보호자</b>에게. 어느 요청의 답인지 함께 보내 보호자 화면이 '요청 중'을 끝낼 수 있게 한다.
	 */
	public void locationUpdated(LocationRequest request, LastLocation last) {
		realtimeEvents.publish(request.getGuardianId(), RealtimeEventType.LOCATION_UPDATED,
				Map.of("location", LocationViewResponse.Last.from(last), "requestId", request.getId()));
	}
}
