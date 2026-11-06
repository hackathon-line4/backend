package com.sonulim.domain.account.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.account.application.AuthService;
import com.sonulim.domain.account.application.TokenService;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "인증·계정")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;
	private final TokenService tokenService;

	public AuthController(AuthService authService, TokenService tokenService) {
		this.authService = authService;
		this.tokenService = tokenService;
	}

	@Operation(summary = "회원가입", description = "A-1 · 인증 없음. 가입 직후 역할은 null")
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ApiResponse.success(AuthResponse.from(authService.signup(request.toCommand())));
	}

	@Operation(summary = "로그인", description = "A-2 · 인증 없음. 같은 이메일로 10분 안에 5번 실패하면 10분 잠금")
	@PostMapping("/login")
	public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.success(AuthResponse.from(authService.login(request.email(), request.password())));
	}

	@Operation(summary = "토큰 갱신", description = "A-2 · 인증 없음. 쓴 갱신 토큰은 바로 무효화되고, 이미 쓴 토큰을 다시 보내면 "
			+ "그 사용자의 갱신 토큰이 모두 무효화된다")
	@PostMapping("/refresh")
	public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return ApiResponse.success(TokenResponse.from(tokenService.refresh(request.refreshToken())));
	}

	@Operation(summary = "로그아웃", description = "A-4 · 공통. 갱신 토큰을 무효화한다. 액세스 토큰은 만료까지 유효")
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody LogoutRequest request) {
		tokenService.revoke(user.userId(), request.refreshToken());
	}
}
