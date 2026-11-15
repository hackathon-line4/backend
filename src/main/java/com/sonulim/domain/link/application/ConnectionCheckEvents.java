package com.sonulim.domain.link.application;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sonulim.domain.link.api.ConnectionCheckResponse;
import com.sonulim.domain.link.domain.ConnectionCheck;
import com.sonulim.domain.link.domain.Link;
import com.sonulim.global.realtime.RealtimeEventPublisher;
import com.sonulim.global.realtime.RealtimeEventType;

/**
 * 연결 확인(T-2)의 SSE 이벤트 발행(docs/realtime.md). 받는 사람과 data 모양은 API 명세의 이벤트 명세 표를 따른다.
 *
 * <p>트랜잭션 안에서 부르면 커밋된 뒤에 나간다(허브 동작).
 */
@Component
public class ConnectionCheckEvents {

	private final RealtimeEventPublisher realtimeEvents;

	public ConnectionCheckEvents(RealtimeEventPublisher realtimeEvents) {
		this.realtimeEvents = realtimeEvents;
	}

	/**
	 * 새 확인이 만들어졌다 → <b>요청자의 상대방</b>에게. 상대 앱은 이 id로 ack를 보낸다(명세의 data는 {@code {checkId}}).
	 */
	public void requested(ConnectionCheck check, UUID counterpartId) {
		realtimeEvents.publish(counterpartId, RealtimeEventType.CONNECTION_CHECK_REQUESTED,
				Map.of("checkId", check.getId()));
	}

	/**
	 * 시간 안에 ack가 와서 확인됐다 → <b>보호자·당사자 둘 다</b>에게(명세의 data는 {@code {check}}).
	 */
	public void updated(ConnectionCheck check, Link link) {
		Map<String, Object> data = Map.of("check", ConnectionCheckResponse.from(check));
		realtimeEvents.publish(link.getGuardianId(), RealtimeEventType.CONNECTION_CHECK_UPDATED, data);
		realtimeEvents.publish(link.getRecipientId(), RealtimeEventType.CONNECTION_CHECK_UPDATED, data);
	}
}
