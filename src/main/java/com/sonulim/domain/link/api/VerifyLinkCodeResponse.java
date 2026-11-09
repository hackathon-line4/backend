package com.sonulim.domain.link.api;

import java.time.LocalDateTime;
import java.util.UUID;

import com.sonulim.domain.link.application.LinkCodeVerification;

public record VerifyLinkCodeResponse(Guardian guardian, LocalDateTime expiresAt) {

	static VerifyLinkCodeResponse from(LinkCodeVerification verification) {
		return new VerifyLinkCodeResponse(new Guardian(verification.guardianId(), verification.guardianName()),
				verification.expiresAt());
	}

	public record Guardian(UUID id, String name) {
	}
}
