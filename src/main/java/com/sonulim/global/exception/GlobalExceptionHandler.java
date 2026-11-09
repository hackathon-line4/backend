package com.sonulim.global.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
		String message = fieldError != null ? fieldError.getDefaultMessage() : "요청 형식이 올바르지 않아요";
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, message, field, null));
	}

	/**
	 * 경로 변수·쿼리 파라미터가 선언된 타입(UUID, enum 등)으로 변환되지 않을 때 — 잘못된
	 * 요청이지 서버 오류가 아니므로 500이 아니라 400으로 내려준다.
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, "요청 형식이 올바르지 않아요", exception.getName(), null));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
		log.error("처리하지 못한 예외", exception);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "잠시 후 다시 시도해 주세요", null, null));
	}
}
