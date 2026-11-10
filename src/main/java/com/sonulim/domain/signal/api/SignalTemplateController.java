package com.sonulim.domain.signal.api;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.application.SignalTemplateQueryService;
import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/signal-templates")
public class SignalTemplateController {

	private final SignalTemplateQueryService signalTemplateQueryService;

	public SignalTemplateController(SignalTemplateQueryService signalTemplateQueryService) {
		this.signalTemplateQueryService = signalTemplateQueryService;
	}

	@GetMapping
	public ApiResponse<SignalTemplateListResponse> list(@RequestParam(required = false) UUID linkId,
			@RequestParam(required = false) String q, @RequestParam(required = false) SignalCategory category,
			@AuthenticationPrincipal AuthenticatedUser user) {
		List<SignalTemplateResponse> items = signalTemplateQueryService
				.list(user.userId(), user.role(), linkId, q, category).stream()
				.map(SignalTemplateResponse::from)
				.toList();
		return ApiResponse.success(new SignalTemplateListResponse(items));
	}
}
