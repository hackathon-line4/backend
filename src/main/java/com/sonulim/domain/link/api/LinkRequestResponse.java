package com.sonulim.domain.link.api;

import java.time.LocalDateTime;
import java.util.UUID;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.link.application.LinkRequestDetails;
import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkRequest;
import com.sonulim.domain.link.domain.LinkRequestStatus;
import com.sonulim.domain.link.domain.LinkStatus;

/**
 * 연결 요청 하나. 승인 전에는 link가 null이다.
 */
public record LinkRequestResponse(UUID id, LinkRequestStatus status, Party guardian, Recipient recipient, String code,
		LocalDateTime requestedAt, LocalDateTime expiresAt, LocalDateTime decidedAt, LinkResponse link) {

	static LinkRequestResponse from(LinkRequestDetails details) {
		LinkRequest request = details.request();
		return new LinkRequestResponse(request.getId(), request.getStatus(), Party.from(details.guardian()),
				Recipient.from(details.recipient()), request.getCode(), request.getRequestedAt(),
				request.getExpiresAt(), request.getDecidedAt(),
				details.link() == null ? null : LinkResponse.from(details.link(), details.guardian(),
						details.recipient()));
	}

	public record Party(UUID id, String name) {

		static Party from(User user) {
			return new Party(user.getId(), user.getName());
		}
	}

	public record Recipient(UUID id, String name, Role role) {

		static Recipient from(User user) {
			return new Recipient(user.getId(), user.getName(), user.getRole());
		}
	}

	public record LinkResponse(UUID id, LinkStatus status, Party guardian, Party recipient, LocalDateTime linkedAt,
			Connection connection) {

		static LinkResponse from(Link link, User guardian, User recipient) {
			return new LinkResponse(link.getId(), link.getStatus(), Party.from(guardian), Party.from(recipient),
					link.getLinkedAt(), Connection.UNKNOWN);
		}
	}

	/**
	 * 연결 상태 확인(T-2) 결과. 연결 확인 기능이 아직 없어서 항상 UNKNOWN이다.
	 */
	public record Connection(String state, LocalDateTime lastConfirmedAt) {

		static final Connection UNKNOWN = new Connection("UNKNOWN", null);
	}
}
