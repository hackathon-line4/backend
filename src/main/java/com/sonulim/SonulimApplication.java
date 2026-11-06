package com.sonulim;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SonulimApplication {

	// main()이 아니라 static 블록에서 고정한다 — @SpringBootTest는 main()을 거치지 않아 테스트에도 적용되게 하기 위해서다.
	static {
		useUtcAsDefaultTimeZone();
	}

	/**
	 * 공통 규칙: 서버 시각은 UTC로 만들고 저장한다. UtcToSeoulLocalDateTimeSerializer가 저장값을 UTC로 가정하므로,
	 * 실행 환경(KST 개발 PC 등)과 관계없이 LocalDateTime.now()와 @CreatedDate가 UTC가 되도록 JVM 기본 시간대를 고정한다.
	 */
	static void useUtcAsDefaultTimeZone() {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	public static void main(String[] args) {
		SpringApplication.run(SonulimApplication.class, args);
	}
}
