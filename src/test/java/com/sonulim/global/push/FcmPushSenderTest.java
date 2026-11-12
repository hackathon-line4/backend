package com.sonulim.global.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;

class FcmPushSenderTest {

	private final UUID recipientId = UUID.randomUUID();
	private final FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
	private final PushTokenRegistry tokenRegistry = mock(PushTokenRegistry.class);
	private final FcmPushSender sender = new FcmPushSender(firebaseMessaging, tokenRegistry);

	private final PushMessage signal = PushMessage.to(recipientId, PushType.SIGNAL)
			.with("linkId", "lnk_1")
			.with("messageId", UUID.fromString("00000000-0000-0000-0000-000000000301"))
			.build();

	// 앱이 리듬을 직접 울려야 해서 표시 알림 없이 데이터만, Doze 중에도 바로 깨우도록 high로 보낸다.
	@Test
	void sendsDataOnlyMessageWithHighAndroidPriorityAndStringValues() throws Exception {
		String json = GsonFactory.getDefaultInstance().toString(FcmPushSender.toFcmMessage("tok_a", signal));

		assertThat(json)
				.contains("\"token\":\"tok_a\"")
				.contains("\"type\":\"SIGNAL\"")
				.contains("\"linkId\":\"lnk_1\"")
				.contains("\"messageId\":\"00000000-0000-0000-0000-000000000301\"")
				.contains("\"priority\":\"high\"")
				.doesNotContain("\"notification\"");
	}

	@Test
	void sendsToEveryTokenOfTheRecipient() throws Exception {
		given(tokenRegistry.tokensOf(recipientId)).willReturn(List.of("tok_a", "tok_b"));

		sender.send(signal);

		verify(firebaseMessaging, times(2)).send(any(Message.class));
	}

	@Test
	void discardsTokenThatFcmReportsAsUnregistered() throws Exception {
		given(tokenRegistry.tokensOf(recipientId)).willReturn(List.of("tok_gone"));
		FirebaseMessagingException error = fcmError(MessagingErrorCode.UNREGISTERED);
		given(firebaseMessaging.send(any(Message.class))).willThrow(error);

		sender.send(signal);

		verify(tokenRegistry).discard("tok_gone");
	}

	// 일시적인 실패로 토큰을 지우면 그 기기는 다시 등록할 때까지 푸시를 못 받는다.
	@Test
	void otherFailuresAreSwallowedAndKeepTheToken() throws Exception {
		given(tokenRegistry.tokensOf(recipientId)).willReturn(List.of("tok_a"));
		FirebaseMessagingException error = fcmError(MessagingErrorCode.UNAVAILABLE);
		given(firebaseMessaging.send(any(Message.class))).willThrow(error);

		assertThatCode(() -> sender.send(signal)).doesNotThrowAnyException();
		verify(tokenRegistry, never()).discard(anyString());
	}

	@Test
	void tokenLookupFailureDoesNotReachTheCaller() {
		given(tokenRegistry.tokensOf(recipientId)).willThrow(new IllegalStateException("db down"));

		assertThatCode(() -> sender.send(signal)).doesNotThrowAnyException();
	}

	// 커밋 전에 보내면 앱이 바로 부른 ack가 아직 저장되지 않은 신호를 못 찾는다.
	@Test
	void insideATransactionItSendsOnlyAfterCommit() throws Exception {
		given(tokenRegistry.tokensOf(recipientId)).willReturn(List.of("tok_a"));
		TransactionSynchronizationManager.initSynchronization();
		try {
			sender.send(signal);
			verify(firebaseMessaging, never()).send(any(Message.class));

			TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}

		verify(firebaseMessaging).send(any(Message.class));
	}

	private static FirebaseMessagingException fcmError(MessagingErrorCode code) {
		FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
		given(exception.getMessagingErrorCode()).willReturn(code);
		return exception;
	}
}
