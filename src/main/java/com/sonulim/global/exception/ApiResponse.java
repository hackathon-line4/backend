package com.sonulim.global.exception;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 공통 규칙 문서의 응답 형식: 성공 {"ok":true,"data":...}, 실패 {"ok":false,"error":{...}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean ok, T data, ErrorBody error) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, String field,
			Map<String, Object> details) {
		return new ApiResponse<>(false, null, new ErrorBody(errorCode.name(), message, field, details));
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ErrorBody(String code, String message, String field, Map<String, Object> details) {
	}
}
