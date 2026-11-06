package com.sonulim.global.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sonulim.global.exception.SonulimException;

/**
 * Authorization: Bearer 토큰을 검증해 SecurityContext에 인증 사용자를 넣는다.
 * 토큰이 잘못됐거나 만료돼도 여기서 응답하지 않고 사유만 요청 속성에 남긴다 — 인증이 필요한 경로라면
 * JsonAuthenticationEntryPoint가 그 사유(UNAUTHORIZED/TOKEN_EXPIRED)로 401을 내고, 공개 경로는 그대로 통과한다.
 * SecurityConfig가 직접 생성한다(@Component로 두면 서블릿 필터로 한 번 더 등록된다).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".AUTH_ERROR";

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider tokenProvider;

	public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
		this.tokenProvider = tokenProvider;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			try {
				AuthenticatedUser user = tokenProvider.parse(header.substring(BEARER_PREFIX.length()));
				List<GrantedAuthority> authorities = user.role() == null
						? List.of()
						: List.of(new SimpleGrantedAuthority("ROLE_" + user.role()));
				SecurityContextHolder.getContext()
						.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
			} catch (SonulimException exception) {
				request.setAttribute(AUTH_ERROR_ATTRIBUTE, exception.getErrorCode());
			}
		}
		filterChain.doFilter(request, response);
	}
}
