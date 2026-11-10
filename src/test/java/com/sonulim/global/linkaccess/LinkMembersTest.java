package com.sonulim.global.linkaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class LinkMembersTest {

	private final UUID guardianId = UUID.randomUUID();
	private final UUID recipientId = UUID.randomUUID();
	private final LinkMembers members = new LinkMembers(UUID.randomUUID(), guardianId, recipientId);

	@Test
	void counterpartOfEachMemberIsTheOtherOne() {
		assertThat(members.counterpartOf(guardianId)).isEqualTo(recipientId);
		assertThat(members.counterpartOf(recipientId)).isEqualTo(guardianId);
	}

	@Test
	void counterpartOfNonMemberIsACallerBug() {
		assertThatThrownBy(() -> members.counterpartOf(UUID.randomUUID()))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
