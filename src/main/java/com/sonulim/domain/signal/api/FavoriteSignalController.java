package com.sonulim.domain.signal.api;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.application.FavoriteSignalCommandService;
import com.sonulim.global.exception.ApiResponse;
import com.sonulim.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links/{linkId}/favorite-signals")
public class FavoriteSignalController {

	private final FavoriteSignalCommandService favoriteSignalCommandService;

	public FavoriteSignalController(FavoriteSignalCommandService favoriteSignalCommandService) {
		this.favoriteSignalCommandService = favoriteSignalCommandService;
	}

	@PutMapping
	public ApiResponse<SignalListResponse> replace(@PathVariable UUID linkId,
			@Valid @RequestBody ReplaceFavoriteSignalsRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
		List<SignalResponse> items = favoriteSignalCommandService
				.replace(linkId, user.userId(), user.role(), request.signalIds()).stream()
				.map(SignalResponse::from)
				.toList();
		return ApiResponse.success(new SignalListResponse(items));
	}
}
