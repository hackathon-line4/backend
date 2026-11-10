package com.sonulim.global.push;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 기기 푸시 토큰을 읽을 창구와 FCM 자격 증명이 아직 없어서, local/test에서만 보낼 내용을 로그로 남기는 임시 구현이다.
 * 실제 발송이 붙으면 이 클래스는 지운다.
 *
 * <p>prod에서는 절대 활성화되지 않는다 — local/test와 prod가 같이 켜진 상태에서도 마찬가지다.
 * 그래서 prod 배포에서는 이 빈이 없어 PushSender를 필요로 하는 서비스가 뜨지 못하고 기동이 실패한다
 * ({@code DevelopmentLinkAccessGuard}와 같은 방식).
 *
 * <p>보낸 내용을 테스트에서 확인할 수 있도록 기록해 둔다.
 */
@Component
@Profile("(local | test) & !prod")
public class LoggingPushSender implements PushSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

	private final List<PushMessage> sent = new ArrayList<>();

	@Override
	public synchronized void send(PushMessage message) {
		sent.add(message);
		log.info("푸시(개발용, 실제 발송 없음) {} → {} {}", message.type(), message.recipientUserId(), message.data());
	}

	public synchronized List<PushMessage> sent() {
		return List.copyOf(sent);
	}

	public synchronized void clear() {
		sent.clear();
	}
}
