package com.sonulim.global.exception;

import java.util.Map;

/**
 * 비즈니스 예외는 RuntimeException을 직접 던지지 않고 이 타입으로 던진다(docs/conventions.md 참고).
 */
public class SonulimException extends RuntimeException {

	private final ErrorCode errorCode;
	private final String field;
	private final Map<String, Object> details;

	public SonulimException(ErrorCode errorCode, String message) {
		this(errorCode, message, null, null);
	}

	public SonulimException(ErrorCode errorCode, String message, String field, Map<String, Object> details) {
		super(message);
		this.errorCode = errorCode;
		this.field = field;
		this.details = details;
	}

	public static SonulimException forField(ErrorCode errorCode, String message, String field) {
		return new SonulimException(errorCode, message, field, null);
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	public String getField() {
		return field;
	}

	public Map<String, Object> getDetails() {
		return details;
	}
}
