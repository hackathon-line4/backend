package com.sonulim.global.push;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 보낼 푸시 한 건. 모든 푸시 데이터에는 {@code type}과 화면을 열 때 필요한 ID가 들어간다(서버 내부 처리).
 *
 * <p>예: {@code {"type": "NO_RESPONSE", "linkId": "lnk_1", "messageId": "msg_301"}}
 */
public record PushMessage(UUID recipientUserId, PushType type, Map<String, Object> data) {

	public static Builder to(UUID recipientUserId, PushType type) {
		return new Builder(recipientUserId, type);
	}

	public static final class Builder {

		private final UUID recipientUserId;
		private final PushType type;
		private final Map<String, Object> data = new LinkedHashMap<>();

		private Builder(UUID recipientUserId, PushType type) {
			this.recipientUserId = recipientUserId;
			this.type = type;
		}

		/** 값이 없는 키는 넣지 않는다 — 앱이 키 존재로 화면을 고르기 때문이다. */
		public Builder with(String key, Object value) {
			if (value != null) {
				data.put(key, value);
			}
			return this;
		}

		public PushMessage build() {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("type", type.name());
			payload.putAll(data);
			return new PushMessage(recipientUserId, type, Map.copyOf(payload));
		}
	}
}
