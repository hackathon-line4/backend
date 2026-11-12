package com.sonulim.global.push;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * FCM 자격 증명 없이 띄운 local/test에서 보낼 내용을 로그로만 남긴다. 자격 증명 경로가 있으면
 * {@link FcmPushSender}가 대신 등록된다({@link FcmConfig}).
 *
 * <p>prod에서는 절대 활성화되지 않는다 — local/test와 prod가 같이 켜진 상태에서도 마찬가지다.
 * 그래서 자격 증명 없는 prod 배포에서는 PushSender가 없어 기동이 실패한다 — 푸시가 조용히 안 나가는 일이 없도록 막아 둔다.
 *
 * <p>보낸 내용을 테스트에서 확인할 수 있도록 기록해 둔다.
 */
@Component
@Profile("(local | test) & !prod")
@ConditionalOnExpression("'${sonulim.firebase.credentials-path:}'.isBlank()")
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
