package com.sonulim.domain.signal.api;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.messaging.api.MessageResponse;
import com.sonulim.domain.signal.application.SignalCommandService;
import com.sonulim.domain.signal.application.SignalPracticeService;
import com.sonulim.domain.signal.application.SignalQueryService;
import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.idempotency.IdempotencyService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links/{linkId}/signals")
public class SignalController {

	private final SignalQueryService signalQueryService;
	private final SignalCommandService signalCommandService;
	private final SignalPracticeService signalPracticeService;
	private final IdempotencyService idempotencyService;

	public SignalController(SignalQueryService signalQueryService, SignalCommandService signalCommandService,
			SignalPracticeService signalPracticeService, IdempotencyService idempotencyService) {
		this.signalQueryService = signalQueryService;
		this.signalCommandService = signalCommandService;
		this.signalPracticeService = signalPracticeService;
		this.idempotencyService = idempotencyService;
	}

	@GetMapping
	public ApiResponse<SignalListResponse> list(@PathVariable UUID linkId,
			@RequestParam(required = false) SignalCategory category,
			@AuthenticationPrincipal AuthenticatedUser user) {
		List<SignalResponse> signals = signalQueryService.list(linkId, user.userId(), user.role(), category).stream()
				.map(SignalResponse::from)
				.toList();
		return ApiResponse.success(new SignalListResponse(signals));
	}

	@GetMapping("/{signalId}")
	public ApiResponse<SignalResponse> detail(@PathVariable UUID linkId, @PathVariable UUID signalId,
			@AuthenticationPrincipal AuthenticatedUser user) {
		SignalResponse response = SignalResponse
				.from(signalQueryService.getDetail(linkId, signalId, user.userId(), user.role()));
		return ApiResponse.success(response);
	}

	@PostMapping
	public ResponseEntity<ApiResponse<SignalResponse>> create(@PathVariable UUID linkId,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody CreateSignalRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
		var signal = signalCommandService.create(linkId, user.userId(), user.role(), idempotencyKey,
				request.toCommand());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(SignalResponse.from(signal)));
	}

	@PatchMapping("/{signalId}")
	public ApiResponse<SignalResponse> update(@PathVariable UUID linkId, @PathVariable UUID signalId,
			@Valid @RequestBody UpdateSignalRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
		var signal = signalCommandService.update(linkId, signalId, user.userId(), user.role(), request.toCommand());
		return ApiResponse.success(SignalResponse.from(signal));
	}

	@DeleteMapping("/{signalId}")
	public ResponseEntity<Void> delete(@PathVariable UUID linkId, @PathVariable UUID signalId,
			@AuthenticationPrincipal AuthenticatedUser user) {
		signalCommandService.delete(linkId, signalId, user.userId(), user.role());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{signalId}/practice")
	public ResponseEntity<ApiResponse<MessageResponse>> practice(@PathVariable UUID linkId,
			@PathVariable UUID signalId,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@AuthenticationPrincipal AuthenticatedUser user) {
		MessageResponse response = idempotencyService.execute(user.userId(), idempotencyKey, signalId,
				MessageResponse.class, () -> signalPracticeService.practice(user, linkId, signalId));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
	}

	@PostMapping("/rhythm-check")
	public ApiResponse<RhythmCheckResponse> checkRhythm(@PathVariable UUID linkId,
			@Valid @RequestBody RhythmCheckRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
		var result = signalQueryService.checkRhythm(linkId, user.userId(), user.role(), request.rhythm(),
				request.excludeSignalId());
		return ApiResponse.success(RhythmCheckResponse.from(result));
	}
}
