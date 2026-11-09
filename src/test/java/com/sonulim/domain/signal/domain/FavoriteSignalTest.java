package com.sonulim.domain.signal.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FavoriteSignalTest {

	@Test
	void holdsTheFieldsItWasCreatedWith() {
		UUID linkId = UUID.randomUUID();
		UUID signalId = UUID.randomUUID();

		FavoriteSignal favorite = new FavoriteSignal(linkId, signalId, 0);

		assertThat(favorite.getLinkId()).isEqualTo(linkId);
		assertThat(favorite.getSignalId()).isEqualTo(signalId);
		assertThat(favorite.getPosition()).isEqualTo(0);
		// id는 Hibernate가 저장 시점에 생성한다(Signal과 같은 관례) — 저장 전엔 null이 맞다.
		assertThat(favorite.getId()).isNull();
	}
}
