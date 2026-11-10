package com.sonulim.domain.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.linkaccess.LinkAccessGuard;
import com.sonulim.global.linkaccess.LinkMembers;

/**
 * 다른 도메인이 주입받는 LinkAccessGuard가 실제 구현이고, 조회용(verifyAccessible)과 전송용(requireMembers)이
 * 해제된 연결을 다르게 다루는지.
 */
@SpringBootTest
@Transactional
class LinkMembershipGuardIntegrationTest {

	@Autowired
	private LinkAccessGuard guard;

	@Autowired
	private LinkRepository linkRepository;

	private final UUID guardianId = UUID.randomUUID();
	private final UUID recipientId = UUID.randomUUID();

	@Test
	void injectedGuardIsTheLinkDomainImplementation() {
		assertThat(guard).isInstanceOf(LinkMembershipGuard.class);
	}

	@Test
	void bothMembersPassVerifyAccessible() {
		Link link = activeLink();

		assertThatCode(() -> guard.verifyAccessible(link.getId(), guardianId)).doesNotThrowAnyException();
		assertThatCode(() -> guard.verifyAccessible(link.getId(), recipientId)).doesNotThrowAnyException();
	}

	@Test
	void outsiderOrUnknownLinkIsNotFoundOnVerifyAccessible() {
		Link link = activeLink();

		assertErrorCode(() -> guard.verifyAccessible(link.getId(), UUID.randomUUID()), ErrorCode.NOT_FOUND);
		assertErrorCode(() -> guard.verifyAccessible(UUID.randomUUID(), guardianId), ErrorCode.NOT_FOUND);
		assertErrorCode(() -> guard.verifyAccessible(null, guardianId), ErrorCode.NOT_FOUND);
	}

	// 조회·관리 API의 명세 오류 목록에 409가 없어서 해제된 연결도 구성원이면 통과한다(#75).
	@Test
	void endedLinkStillPassesVerifyAccessibleForMembers() {
		Link link = endedLink();

		assertThatCode(() -> guard.verifyAccessible(link.getId(), guardianId)).doesNotThrowAnyException();
		assertThatCode(() -> guard.verifyAccessible(link.getId(), recipientId)).doesNotThrowAnyException();
	}

	@Test
	void requireMembersReturnsBothMembersOfActiveLink() {
		Link link = activeLink();

		LinkMembers members = guard.requireMembers(link.getId(), recipientId);

		assertThat(members).isEqualTo(new LinkMembers(link.getId(), guardianId, recipientId));
		assertThat(members.counterpartOf(recipientId)).isEqualTo(guardianId);
		assertThat(members.counterpartOf(guardianId)).isEqualTo(recipientId);
	}

	@Test
	void requireMembersOnEndedLinkIsLinkEndedForBothMembers() {
		Link link = endedLink();

		assertErrorCode(() -> guard.requireMembers(link.getId(), guardianId), ErrorCode.LINK_ENDED);
		assertErrorCode(() -> guard.requireMembers(link.getId(), recipientId), ErrorCode.LINK_ENDED);
	}

	// 구성원을 먼저 보므로 남의 해제된 연결은 409가 아니라 404 — 연결이 있었다는 사실을 드러내지 않는다.
	@Test
	void endedLinkOfSomeoneElseIsNotFoundOnRequireMembers() {
		Link link = endedLink();

		assertErrorCode(() -> guard.requireMembers(link.getId(), UUID.randomUUID()), ErrorCode.NOT_FOUND);
	}

	@Test
	void unknownLinkIsNotFoundOnRequireMembers() {
		assertErrorCode(() -> guard.requireMembers(UUID.randomUUID(), guardianId), ErrorCode.NOT_FOUND);
	}

	private Link activeLink() {
		return linkRepository.saveAndFlush(Link.connect(guardianId, recipientId, LocalDateTime.now()));
	}

	private Link endedLink() {
		Link link = activeLink();
		link.end(LocalDateTime.now());
		return linkRepository.saveAndFlush(link);
	}

	private static void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
		assertThatThrownBy(call)
				.isInstanceOf(SonulimException.class)
				.extracting(exception -> ((SonulimException) exception).getErrorCode())
				.isEqualTo(expected);
	}
}
