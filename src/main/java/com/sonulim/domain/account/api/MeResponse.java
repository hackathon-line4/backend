package com.sonulim.domain.account.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sonulim.domain.account.application.MeDetails;
import com.sonulim.domain.account.application.UserSummary;
import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.link.domain.ConnectionState;
import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkRequest;
import com.sonulim.domain.link.domain.LinkRequestStatus;
import com.sonulim.domain.link.domain.LinkStatus;

/**
 * 앱 시작(스플래시) 때 화면 분기에 쓰는 내 정보. links는 ACTIVE 연결, pendingLinkRequest는 당사자가 보낸 대기 중 연결 요청
 * (보호자는 항상 null). 연결·연결 요청의 모양은 link API 응답과 같다(Notion API 명세).
 */
public record MeResponse(UserResponse user, List<LinkItem> links, PendingLinkRequest pendingLinkRequest) {

	public static MeResponse from(MeDetails details) {
		Map<UUID, UserSummary> people = details.people();
		List<LinkItem> links = details.links().stream().map(link -> LinkItem.from(link, people)).toList();
		PendingLinkRequest pending = details.pendingLinkRequest() == null
				? null
				: PendingLinkRequest.from(details.pendingLinkRequest(), people);
		return new MeResponse(UserResponse.from(details.user()), links, pending);
	}

	public record Party(UUID id, String name) {

		static Party of(UUID id, Map<UUID, UserSummary> people) {
			UserSummary person = people.get(id);
			return new Party(id, person == null ? null : person.name());
		}
	}

	public record RequestRecipient(UUID id, String name, Role role) {

		static RequestRecipient of(UUID id, Map<UUID, UserSummary> people) {
			UserSummary person = people.get(id);
			return new RequestRecipient(id, person == null ? null : person.name(), person == null ? null : person.role());
		}
	}

	/**
	 * 연결 확인(T-2) 결과. 확인한 적 없으면 UNKNOWN.
	 */
	public record Connection(ConnectionState state, LocalDateTime lastConfirmedAt) {
	}

	public record LinkItem(UUID id, LinkStatus status, Party guardian, Party recipient, LocalDateTime linkedAt,
			Connection connection) {

		static LinkItem from(Link link, Map<UUID, UserSummary> people) {
			return new LinkItem(link.getId(), link.getStatus(), Party.of(link.getGuardianId(), people),
					Party.of(link.getRecipientId(), people), link.getLinkedAt(),
					new Connection(link.getConnectionState(), link.getLastConfirmedAt()));
		}
	}

	/**
	 * 승인 전 요청이라 link는 항상 null이다(link API의 연결 요청 응답과 같은 모양).
	 */
	public record PendingLinkRequest(UUID id, LinkRequestStatus status, Party guardian, RequestRecipient recipient,
			String code, LocalDateTime requestedAt, LocalDateTime expiresAt, LocalDateTime decidedAt, Object link) {

		static PendingLinkRequest from(LinkRequest request, Map<UUID, UserSummary> people) {
			return new PendingLinkRequest(request.getId(), request.getStatus(),
					Party.of(request.getGuardianId(), people), RequestRecipient.of(request.getRecipientId(), people),
					request.getCode(), request.getRequestedAt(), request.getExpiresAt(), request.getDecidedAt(), null);
		}
	}
}
