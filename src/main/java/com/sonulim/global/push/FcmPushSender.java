package com.sonulim.global.push;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;

/**
 * FCM으로 실제 발송한다. FCM 자격 증명이 있을 때만 등록된다({@link FcmConfig}).
 *
 * <p>모든 종류를 표시 없는 데이터 메시지(data-only)로, Android 우선순위 high로 보낸다. 알림 문구·채널·진동은
 * 앱이 {@code type}을 보고 정한다 — 서버에는 문구가 없고, 신호처럼 앱이 직접 리듬을 울려야 하는 종류가 있기 때문이다.
 *
 * <p>트랜잭션 안에서 불리면 커밋된 뒤에 보낸다. 커밋 전에 보내면 앱이 푸시를 받고 바로 부른 API(ack 등)가
 * 아직 저장되지 않은 기록을 찾지 못할 수 있고, 롤백된 작업의 푸시가 나갈 수도 있다.
 * 발송 실패는 로그만 남기고 호출한 쪽으로 던지지 않는다({@link PushSender}).
 */
public class FcmPushSender implements PushSender {

	private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

	private final FirebaseMessaging firebaseMessaging;
	private final PushTokenRegistry tokenRegistry;

	public FcmPushSender(FirebaseMessaging firebaseMessaging, PushTokenRegistry tokenRegistry) {
		this.firebaseMessaging = firebaseMessaging;
		this.tokenRegistry = tokenRegistry;
	}

	@Override
	public void send(PushMessage message) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					deliver(message);
				}
			});
			return;
		}
		deliver(message);
	}

	private void deliver(PushMessage message) {
		try {
			for (String token : tokenRegistry.tokensOf(message.recipientUserId())) {
				deliverTo(token, message);
			}
		} catch (RuntimeException e) {
			log.warn("푸시 발송 실패 {} → {}", message.type(), message.recipientUserId(), e);
		}
	}

	private void deliverTo(String token, PushMessage message) {
		try {
			firebaseMessaging.send(toFcmMessage(token, message));
		} catch (FirebaseMessagingException e) {
			if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
				tokenRegistry.discard(token);
				log.info("유효하지 않은 푸시 토큰 삭제 {} → {}", message.type(), message.recipientUserId());
				return;
			}
			log.warn("푸시 발송 실패 {} → {} ({})", message.type(), message.recipientUserId(),
					e.getMessagingErrorCode(), e);
		}
	}

	static Message toFcmMessage(String token, PushMessage message) {
		return Message.builder()
				.setToken(token)
				.putAllData(stringValues(message.data()))
				.setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
				.build();
	}

	// FCM data 값은 문자열만 받는다.
	private static Map<String, String> stringValues(Map<String, Object> data) {
		Map<String, String> values = new LinkedHashMap<>();
		data.forEach((key, value) -> values.put(key, String.valueOf(value)));
		return values;
	}
}
