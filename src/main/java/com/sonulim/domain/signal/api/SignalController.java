package com.sonulim.domain.signal.api;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.application.SignalCommandService;
import com.sonulim.domain.signal.application.SignalQueryService;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links/{linkId}/signals")
public class SignalController {

	private final SignalQueryService signalQueryService;
	private final SignalCommandService signalCommandService;

	public SignalController(SignalQueryService signalQueryService, SignalCommandService signalCommandService) {
		this.signalQueryService = signalQueryService;
		this.signalCommandService = signalCommandService;
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

	@PostMapping
	public ResponseEntity<ApiResponse<SignalResponse>> create(@PathVariable UUID linkId,
			@Valid @RequestBody CreateSignalRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
		var signal = signalCommandService.create(linkId, user.userId(), request.name(), request.description(),
				request.category(), request.icon(), request.rhythm());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(SignalResponse.from(signal)));
	}

	@PatchMapping("/{signalId}")
	public ApiResponse<SignalResponse> update(@PathVariable UUID linkId, @PathVariable UUID signalId,
			@Valid @RequestBody UpdateSignalRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
		var signal = signalCommandService.update(linkId, signalId, user.userId(), request.toCommand());
		return ApiResponse.success(SignalResponse.from(signal));
	}

	@DeleteMapping("/{signalId}")
	public ResponseEntity<Void> delete(@PathVariable UUID linkId, @PathVariable UUID signalId,
			@AuthenticationPrincipal AuthenticatedUser user) {
		signalCommandService.delete(linkId, signalId, user.userId());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/rhythm-check")
	public ApiResponse<RhythmCheckResponse> checkRhythm(@PathVariable UUID linkId,
			@Valid @RequestBody RhythmCheckRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
		var result = signalQueryService.checkRhythm(linkId, user.userId(), request.rhythm());
		return ApiResponse.success(RhythmCheckResponse.from(result));
	}
}
