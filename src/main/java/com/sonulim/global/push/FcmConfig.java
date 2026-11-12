package com.sonulim.global.push;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

/**
 * {@code sonulim.firebase.credentials-path}(환경 변수 FIREBASE_CREDENTIALS_PATH)에 서비스 계정 키 경로가 있으면
 * FCM으로 실제 발송한다. 비어 있으면 이 설정은 꺼지고 local/test에서는 {@link LoggingPushSender}가 대신한다.
 * prod는 비어 있으면 PushSender가 없어 기동이 실패한다.
 *
 * <p>키 파일은 레포에 넣지 않는다(.gitignore의 secret/).
 */
@Configuration
@ConditionalOnExpression("!'${sonulim.firebase.credentials-path:}'.isBlank()")
class FcmConfig {

	private static final String APP_NAME = "sonulim";

	@Bean
	FirebaseMessaging firebaseMessaging(@Value("${sonulim.firebase.credentials-path}") String credentialsPath)
			throws IOException {
		return FirebaseMessaging.getInstance(firebaseApp(Path.of(credentialsPath)));
	}

	@Bean
	FcmPushSender fcmPushSender(FirebaseMessaging firebaseMessaging, PushTokenRegistry tokenRegistry) {
		return new FcmPushSender(firebaseMessaging, tokenRegistry);
	}

	// 같은 JVM에서 컨텍스트가 여러 번 뜨면(테스트) 이미 만든 앱을 다시 쓴다.
	private static FirebaseApp firebaseApp(Path credentialsPath) throws IOException {
		for (FirebaseApp app : FirebaseApp.getApps()) {
			if (app.getName().equals(APP_NAME)) {
				return app;
			}
		}
		try (InputStream in = Files.newInputStream(credentialsPath)) {
			FirebaseOptions options = FirebaseOptions.builder()
					.setCredentials(GoogleCredentials.fromStream(in))
					.build();
			return FirebaseApp.initializeApp(options, APP_NAME);
		}
	}
}
