package com.sonulim.global.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.fasterxml.jackson.databind.JsonMappingException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final String INVALID_REQUEST = "요청 형식이 올바르지 않아요";

	@ExceptionHandler(SonulimException.class)
	public ResponseEntity<ApiResponse<Void>> handleSonulimException(SonulimException exception) {
		ErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(errorCode.getHttpStatus())
				.body(ApiResponse.error(errorCode, exception.getMessage(), exception.getField(), exception.getDetails()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
		FieldError fieldError = exception.getBindingResult().getFieldError();
		String field = fieldError != null ? fieldError.getField() : null;
		String message = fieldError != null ? fieldError.getDefaultMessage() : INVALID_REQUEST;
		return validationFailed(message, field);
	}

	/**
	 * 본문을 읽지 못했다 — 깨진 JSON, 본문 없음, enum·날짜처럼 타입이 맞지 않는 값.
	 * 잡지 않으면 공통 규칙의 400 VALIDATION_FAILED 대신 500 INTERNAL_ERROR가 나간다.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException exception) {
		String field = exception.getCause() instanceof InvalidFormatException invalidFormat
				? lastFieldName(invalidFormat) : null;
		return validationFailed(INVALID_REQUEST, field);
	}

	/** 경로 변수나 쿼리 파라미터의 타입이 맞지 않는다(UUID 자리에 아무 문자열 등). */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		return validationFailed(INVALID_REQUEST, exception.getName());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
		log.error("처리하지 못한 예외", exception);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "잠시 후 다시 시도해 주세요", null, null));
	}

	private static ResponseEntity<ApiResponse<Void>> validationFailed(String message, String field) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, message, field, null));
	}

	/** 어느 칸이 잘못됐는지만 알려 준다. 들어온 값이나 내부 타입 이름은 응답에 내보내지 않는다. */
	private static String lastFieldName(InvalidFormatException exception) {
		String name = null;
		for (JsonMappingException.Reference reference : exception.getPath()) {
			if (reference.getFieldName() != null) {
				name = reference.getFieldName();
			}
		}
		return name;
	}
}
