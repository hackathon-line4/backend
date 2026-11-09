package com.sonulim.domain.signal.api;

import java.util.List;

import com.sonulim.domain.signal.domain.SignalCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSignalRequest(
		@NotBlank @Size(max = 12) String name,
		String description,
		@NotNull SignalCategory category,
		String icon,
		@NotNull List<String> rhythm) {
}
