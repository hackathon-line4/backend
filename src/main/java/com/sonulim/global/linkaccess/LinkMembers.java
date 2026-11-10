package com.sonulim.global.linkaccess;

import java.util.UUID;

/**
 * 연결의 구성원. 다른 도메인이 전송 기록을 만들 때 보호자·당사자 id를 채우는 데 쓴다.
 * 이름 등 사용자 정보는 account의 UserQueryService로 읽는다. 필요한 값이 늘면 그때 넓힌다(#75).
 */
public record LinkMembers(UUID linkId, UUID guardianId, UUID recipientId) {

	/**
	 * userId의 상대방. 구성원이 아닌 id를 넘기면 호출한 쪽의 잘못이라 IllegalArgumentException을 던진다 —
	 * requireMembers를 통과한 요청자만 넘기면 생기지 않는다.
	 */
	public UUID counterpartOf(UUID userId) {
		if (guardianId.equals(userId)) {
			return recipientId;
		}
		if (recipientId.equals(userId)) {
			return guardianId;
		}
		throw new IllegalArgumentException("이 연결의 구성원이 아닙니다: " + userId);
	}
}
