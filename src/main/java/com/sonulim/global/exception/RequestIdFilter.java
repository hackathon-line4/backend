package com.sonulim.global.exception;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 공통 규칙: 모든 응답에 X-Request-Id 헤더를 붙인다. 오류를 제보할 때 이 값을 함께 전달한다.
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER_NAME = "X-Request-Id";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		response.setHeader(HEADER_NAME, UUID.randomUUID().toString());
		filterChain.doFilter(request, response);
	}
}
