package com.sonulim.domain.link.api;

import java.util.List;

import com.sonulim.domain.link.application.LinkRequestDetails;

public record LinkRequestListResponse(List<LinkRequestResponse> items) {

	static LinkRequestListResponse from(List<LinkRequestDetails> details) {
		return new LinkRequestListResponse(details.stream().map(LinkRequestResponse::from).toList());
	}
}
