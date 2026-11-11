package com.sonulim.domain.link.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.application.UserQueryService;
import com.sonulim.domain.account.application.UserSummary;
import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.history.application.HistoryService;
import com.sonulim.domain.history.application.HistoryService.HomeRecords;
import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkRepository;
import com.sonulim.domain.signal.application.FavoriteSignalQueryService;
import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.linkaccess.LinkAccessGuard;
import com.sonulim.global.security.AuthenticatedUser;

/**
 * 홈 요약(S-4, 예외로 link 도메인, #81). 보호자만, 연결 구성원만(조회 API라 해제된 연결도 정상 응답).
 *
 * <p>다른 도메인은 각자의 조회 서비스로 읽는다 — 사용자는 account `UserQueryService`, 자주 쓰는 신호는 signal
 * `FavoriteSignalQueryService`, 기록·상태는 history `HistoryService`(messaging·recipient를 읽는 곳은 history 한 곳).
 * 위치는 정책 확정 전이라 응답에서 고정값이다.
 */
@Service
public class HomeService {

	static final int RECENT_ACTIVITY_LIMIT = 3;
	static final int FAVORITE_LIMIT = 6;

	private final LinkAccessGuard linkAccessGuard;
	private final LinkRepository linkRepository;
	private final UserQueryService userQueryService;
	private final FavoriteSignalQueryService favoriteSignalQueryService;
	private final HistoryService historyService;
	private final Clock clock;

	public HomeService(LinkAccessGuard linkAccessGuard, LinkRepository linkRepository,
			UserQueryService userQueryService, FavoriteSignalQueryService favoriteSignalQueryService,
			HistoryService historyService, Clock clock) {
		this.linkAccessGuard = linkAccessGuard;
		this.linkRepository = linkRepository;
		this.userQueryService = userQueryService;
		this.favoriteSignalQueryService = favoriteSignalQueryService;
		this.historyService = historyService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public HomeSummary get(AuthenticatedUser user, UUID linkId) {
		RoleRequirement.require(user, Role.GUARDIAN);
		linkAccessGuard.verifyAccessible(linkId, user.userId());
		Link link = linkRepository.findById(linkId).orElseThrow(HomeService::notFound);
		// 탈퇴 등으로 당사자 정보가 없으면 404(명세 해석, Pending Decisions).
		UserSummary recipient = userQueryService.find(link.getRecipientId()).orElseThrow(HomeService::notFound);

		HomeRecords records = historyService.homeRecords(linkId, user.userId(), RECENT_ACTIVITY_LIMIT);
		RecipientStatus status = RecipientStatus.decide(records.latestUnopenedHelpRequest(),
				records.latestGuardianSignal(), LocalDateTime.now(clock));
		List<Signal> favorites = favoriteSignalQueryService.list(linkId).stream().limit(FAVORITE_LIMIT).toList();
		return new HomeSummary(link, recipient, status, records.latestRespondedSignal(), favorites,
				records.recent());
	}

	private static SonulimException notFound() {
		return new SonulimException(ErrorCode.NOT_FOUND, "연결을 찾을 수 없어요");
	}
}
