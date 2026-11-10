package com.sonulim.domain.messaging.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기획·프론트와 아직 확정하지 못한 값들(docs/domains/messaging.md의 Pending Decisions).
 * 코드에 상수로 박지 않고 설정으로 빼 둬서, 확정되면 yml만 고치면 되게 한다.
 *
 * @param ackTimeout                 신호를 보낸 뒤 이만큼 ack가 없으면 UNCONFIRMED(백엔드 제안 60초)
 * @param emergencyWarningDelay       긴급 신호가 전달 후 이만큼 응답이 없으면 보호자에게 경고(백엔드 제안 30초)
 * @param defaultResponseWaitMinutes 응답 대기 시간. 원래는 보호자가 5/10/30분 중에 고른 값(notification_settings)을
 *                                   써야 하지만 settings 도메인 구현 전까지 이 기본값을 쓴다
 * @param emergencyPlayback          긴급 신호를 받은 기기가 따를 재생 정책. 일반 신호는 앱 기본값(null)을 쓴다
 */
@ConfigurationProperties(prefix = "sonulim.messaging")
public record MessagingProperties(Duration ackTimeout, Duration emergencyWarningDelay, int defaultResponseWaitMinutes,
		EmergencyPlayback emergencyPlayback) {

	/**
	 * 기능 명세서 제안표 그대로(10초 반복, 최대 1분, 방해 금지 무시). 기획 확정 전이라 설정으로 둔다.
	 */
	public record EmergencyPlayback(int repeatIntervalSec, int maxDurationSec, boolean bypassDnd) {
	}
}
