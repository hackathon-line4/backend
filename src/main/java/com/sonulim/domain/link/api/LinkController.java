package com.sonulim.domain.link.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sonulim.domain.link.application.LinkService;
import com.sonulim.global.OpenApiConfig;
import com.sonulim.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "연결")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

	private final LinkService linkService;

	public LinkController(LinkService linkService) {
		this.linkService = linkService;
	}

	@Operation(summary = "연결 해제", description = "L-4 · 보호자. 연결을 ENDED로 바꾼다(행 삭제 아님). 해제 뒤 두 사람 모두 "
			+ "다시 연결할 수 있다. 이미 해제된 연결도 204")
	@DeleteMapping("/{linkId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void end(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String linkId) {
		linkService.end(user, linkId);
	}
}
