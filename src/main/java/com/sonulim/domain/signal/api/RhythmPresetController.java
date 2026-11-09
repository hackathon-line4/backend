package com.sonulim.domain.signal.api;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.application.RhythmPresetQueryService;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rhythm-presets")
public class RhythmPresetController {

	private final RhythmPresetQueryService rhythmPresetQueryService;

	public RhythmPresetController(RhythmPresetQueryService rhythmPresetQueryService) {
		this.rhythmPresetQueryService = rhythmPresetQueryService;
	}

	@GetMapping
	public ApiResponse<RhythmPresetListResponse> list(@RequestParam UUID linkId,
			@AuthenticationPrincipal AuthenticatedUser user) {
		List<RhythmPresetResponse> items = rhythmPresetQueryService.list(user.userId(), user.role(), linkId).stream()
				.map(RhythmPresetResponse::from)
				.toList();
		return ApiResponse.success(new RhythmPresetListResponse(items));
	}
}
