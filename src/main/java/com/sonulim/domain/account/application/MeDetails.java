package com.sonulim.domain.account.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkRequest;

/**
 * GET /me 응답에 필요한 것: 나, ACTIVE 연결, 대기 중 연결 요청(없으면 null), 응답에 이름을 넣을 사람들.
 */
public record MeDetails(User user, List<Link> links, LinkRequest pendingLinkRequest, Map<UUID, UserSummary> people) {
}
