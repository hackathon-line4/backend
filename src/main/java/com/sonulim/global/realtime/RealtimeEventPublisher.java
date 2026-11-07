package com.sonulim.global.realtime;

import java.util.UUID;

/**
 * 도메인 → 실시간 허브. 각 도메인은 이벤트를 이걸로 발행만 한다(docs/architecture.md "실시간 전달", 사용법은 docs/realtime.md).
 */
public interface RealtimeEventPublisher {

	/**
	 * userId에게 이벤트를 보낸다.
	 * <ul>
	 *   <li>트랜잭션 안에서 부르면 커밋된 뒤에 보내고, 롤백되면 보내지 않는다.</li>
	 *   <li>지금 연결이 없어도 5분 동안 보관했다가 재연결(Last-Event-ID) 때 다시 보낸다.</li>
	 *   <li>data는 Spring ObjectMapper로 직렬화한다(LocalDateTime → +09:00). Entity가 아니라 응답 DTO를 넘긴다.</li>
	 * </ul>
	 * 보호자·당사자 둘 다 받아야 하는 이벤트는 사람마다 한 번씩 부른다.
	 */
	void publish(UUID userId, RealtimeEventType type, Object data);
}
