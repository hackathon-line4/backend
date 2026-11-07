package com.sonulim.domain.link.api;

import java.time.LocalDateTime;

import com.sonulim.domain.link.domain.LinkCode;

/**
 * 연결 코드와 같은 값의 QR 내용. 스킴 철자(sonullim)는 Notion API 명세 그대로다.
 */
public record LinkCodeResponse(String code, String qrPayload, LocalDateTime createdAt, LocalDateTime expiresAt) {

	private static final String QR_PAYLOAD_PREFIX = "sonullim://link?code=";

	static LinkCodeResponse from(LinkCode linkCode) {
		return new LinkCodeResponse(linkCode.getCode(), QR_PAYLOAD_PREFIX + linkCode.getCode(),
				linkCode.getIssuedAt(), linkCode.getExpiresAt());
	}
}
