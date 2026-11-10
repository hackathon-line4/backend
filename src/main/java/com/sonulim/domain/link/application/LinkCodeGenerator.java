package com.sonulim.domain.link.application;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * 6자리 숫자 연결 코드 후보를 뽑는다. 추측하기 어렵도록 암호학적 난수를 쓴다(Notion API 명세).
 * 살아 있는 코드와 겹치는지는 LinkCodeService가 확인한다.
 */
@Component
public class LinkCodeGenerator {

	private static final int CODE_SPACE = 1_000_000;

	private final SecureRandom random = new SecureRandom();

	public String next() {
		return String.format("%06d", random.nextInt(CODE_SPACE));
	}
}
