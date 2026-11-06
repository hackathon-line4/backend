package com.sonulim.domain.signal.api;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.application.SignalQueryService;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links/{linkId}/signals")
public class SignalController {

	private final SignalQueryService signalQueryService;

	public SignalController(SignalQueryService signalQueryService) {
		this.signalQueryService = signalQueryService;
	}

	@GetMapping
	public ApiResponse<List<SignalResponse>> list(@PathVariable UUID linkId,
			@AuthenticationPrincipal AuthenticatedUser user) {
		List<SignalResponse> signals = signalQueryService.list(linkId, user.userId()).stream()
				.map(SignalResponse::from)
				.toList();
		return ApiResponse.success(signals);
	}

	@GetMapping("/{signalId}")
	public ApiResponse<SignalResponse> detail(@PathVariable UUID linkId, @PathVariable UUID signalId,
			@AuthenticationPrincipal AuthenticatedUser user) {
		SignalResponse response = SignalResponse.from(signalQueryService.getDetail(linkId, signalId, user.userId()));
		return ApiResponse.success(response);
	}
}
