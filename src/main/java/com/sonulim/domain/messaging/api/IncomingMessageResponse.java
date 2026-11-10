package com.sonulim.domain.messaging.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.sonulim.domain.messaging.application.MessagingProperties;
import com.sonulim.domain.messaging.domain.Message;
import com.sonulim.domain.messaging.domain.MessageKind;

/**
 * 당사자 기기가 바로 울릴 수 있도록 리듬과 재생 정책을 함께 담는다.
 * SIGNAL·PRACTICE 푸시 데이터, message.created 이벤트, 받은 신호 조회가 모두 이 모양이다(공통 데이터 형식).
 */
public record IncomingMessageResponse(UUID id, MessageKind kind, boolean isEmergency, String guardianName,
		Signal signal, PlaybackPolicy playbackPolicy, LocalDateTime waitUntil, LocalDateTime sentAt) {

	/**
	 * @param pulses 기기가 실제로 이 배열을 보고 울린다. 기본 신호는 rhythm이 null일 수 있으므로
	 *               리듬만 보내면 진동이 아예 울리지 않는다
	 */
	public record Signal(String name, List<String> rhythm, List<Integer> pulses, int durationMs) {
	}

	/** null은 앱 기본값을 쓰라는 뜻이다. 긴급 신호만 값이 채워진다. */
	public record PlaybackPolicy(Integer repeatIntervalSec, Integer maxDurationSec, boolean bypassDnd) {
	}

	public static IncomingMessageResponse of(Message message, String guardianName,
			MessagingProperties.EmergencyPlayback emergency) {
		var snapshot = message.getSignalSnapshot();
		return new IncomingMessageResponse(message.getId(), message.getKind(), message.isEmergency(), guardianName,
				new Signal(snapshot.name(), snapshot.rhythm(), snapshot.pulses(), snapshot.durationMs()),
				playbackPolicy(message, emergency), message.getWaitUntil(), message.getRequestedAt());
	}

	private static PlaybackPolicy playbackPolicy(Message message, MessagingProperties.EmergencyPlayback emergency) {
		if (!message.isEmergency()) {
			return new PlaybackPolicy(null, null, false);
		}
		return new PlaybackPolicy(emergency.repeatIntervalSec(), emergency.maxDurationSec(), emergency.bypassDnd());
	}
}
