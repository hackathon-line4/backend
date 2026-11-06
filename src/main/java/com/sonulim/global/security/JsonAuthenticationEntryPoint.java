package com.sonulim.global.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.exception.ErrorCode;

/**
 * 인증이 필요한 경로에 인증 없이 들어온 요청을 공통 응답 형식의 401로 바꾼다.
 * 필터 단계라 GlobalExceptionHandler를 거치지 않으므로 여기서 직접 쓴다.
 */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		ErrorCode errorCode = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE) instanceof ErrorCode code
				? code
				: ErrorCode.UNAUTHORIZED;
		String message = errorCode == ErrorCode.TOKEN_EXPIRED ? "토큰이 만료되었습니다" : "다시 로그인해 주세요";

		response.setStatus(errorCode.getHttpStatus());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getWriter(), ApiResponse.error(errorCode, message, null, null));
	}
}
