package com.sonulim.global.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

/**
 * 전송·생성 API의 중복 방지(공통 규칙). 같은 Idempotency-Key로 다시 오면 작업을 또 하지 않고 처음 응답을 그대로 돌려준다.
 *
 * <p>앱은 네트워크 오류로 응답을 못 받으면 같은 키로 재시도한다. 이때 신호가 두 번 울리거나 도움 요청이 두 건
 * 생기면 안 되므로, 작업보다 먼저 (user_id, key) 자리를 잡고 유니크 제약으로 한 쪽만 통과시킨다.
 *
 * <p>사용 예
 * <pre>
 * return idempotencyService.execute(user.userId(), idempotencyKey, request, MessageResponse.class,
 *         () -&gt; MessageResponse.from(messageService.send(...)));
 * </pre>
 */
@Service
public class IdempotencyService {

	private final IdempotencyRecordRepository repository;
	private final ObjectMapper objectMapper;

	public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper) {
		this.repository = repository;
		this.objectMapper = objectMapper;
	}

	/**
	 * @param keyHeader    Idempotency-Key 헤더 값. 없거나 UUID가 아니면 거절한다
	 * @param request      요청 본문. 같은 키를 다른 요청에 재사용했는지 가리는 데만 쓴다
	 * @param responseType 저장해 둔 응답을 되살릴 타입
	 * @param action       처음 들어온 요청일 때만 실행된다
	 */
	@Transactional
	public <T> T execute(UUID userId, String keyHeader, Object request, Class<T> responseType, Supplier<T> action) {
		UUID key = parseKey(keyHeader);
		String requestHash = hash(request);

		T replayed = replay(userId, key, requestHash, responseType);
		if (replayed != null) {
			return replayed;
		}

		IdempotencyRecord record;
		try {
			record = repository.saveAndFlush(IdempotencyRecord.claim(userId, key, requestHash));
		}
		catch (DataIntegrityViolationException sameKeyArrivedFirst) {
			T other = replay(userId, key, requestHash, responseType);
			if (other != null) {
				return other;
			}
			throw inFlight();
		}

		T result = action.get();
		record.completeWith(toMap(result));
		return result;
	}

	/** 처음 응답이 있으면 그대로 돌려준다. 아직 없으면 null. */
	private <T> T replay(UUID userId, UUID key, String requestHash, Class<T> responseType) {
		IdempotencyRecord existing = repository.findByUserIdAndKey(userId, key).orElse(null);
		if (existing == null) {
			return null;
		}
		if (!existing.matches(requestHash)) {
			throw new SonulimException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
					"같은 키로 다른 요청을 보낼 수 없어요");
		}
		if (existing.isInFlight()) {
			throw inFlight();
		}
		return objectMapper.convertValue(existing.getResponse(), responseType);
	}

	/**
	 * 같은 키의 앞선 요청이 아직 처리 중이다. 명세에 이 경우가 정의되어 있지 않아 거절하는 쪽으로 둔다 —
	 * 다시 수행하면 신호가 두 번 울리는데, 그 편이 오류보다 나쁘다(docs/conventions.md의 Pending 참고).
	 */
	private static SonulimException inFlight() {
		return new SonulimException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "같은 요청을 처리하고 있어요. 잠시 후 다시 시도해 주세요");
	}

	private static UUID parseKey(String keyHeader) {
		if (keyHeader == null || keyHeader.isBlank()) {
			throw new SonulimException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key 헤더가 필요해요");
		}
		try {
			return UUID.fromString(keyHeader.trim());
		}
		catch (IllegalArgumentException notAUuid) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED,
					"Idempotency-Key는 UUID 형식이어야 해요", "Idempotency-Key");
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> toMap(Object response) {
		return objectMapper.convertValue(response, Map.class);
	}

	private String hash(Object request) {
		try {
			byte[] json = objectMapper.writeValueAsBytes(request);
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
		}
		catch (JsonProcessingException | NoSuchAlgorithmException cannotHash) {
			throw new IllegalStateException("요청 본문을 해시할 수 없습니다", cannotHash);
		}
	}
}
