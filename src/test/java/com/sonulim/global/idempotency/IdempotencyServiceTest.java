package com.sonulim.global.idempotency;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 트랜잭션으로 감싸지 않는다 — 작업이 실패했을 때 자리가 롤백되는지를 봐야 하는데,
 * 테스트가 같은 트랜잭션 안에 있으면 그 동작이 가려진다.
 */
@SpringBootTest
class IdempotencyServiceTest {

	@Autowired
	private IdempotencyService idempotencyService;

	@Autowired
	private IdempotencyRecordRepository repository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private record SendRequest(String signalId) {
	}

	private record SendResponse(String id, String status, List<String> rhythm) {
	}

	/** 실제 응답에는 거의 항상 시각이 들어간다. */
	private record TimedResponse(String id, LocalDateTime requestedAt, LocalDateTime deliveredAt) {
	}

	private UUID userId;
	private String key;
	private AtomicInteger runs;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		userId = UUID.randomUUID();
		key = UUID.randomUUID().toString();
		runs = new AtomicInteger();
	}

	private SendResponse send(String keyHeader, SendRequest request) {
		return idempotencyService.execute(userId, keyHeader, request, SendResponse.class, () -> {
			runs.incrementAndGet();
			return new SendResponse("msg_" + runs.get(), "REQUESTED", List.of("SHORT", "LONG"));
		});
	}

	@Test
	void aResponseWithTimestampsCanBeReplayed() {
		// 앱의 ObjectMapper는 LocalDateTime을 +09:00 문자열로 쓰는데 그 형식을 다시 읽지는 못한다.
		// 보관용 변환기를 따로 쓰지 않으면 시각이 든 응답은 재시도가 전부 깨진다.
		LocalDateTime requestedAt = LocalDateTime.of(2026, 11, 4, 18, 2, 1);
		TimedResponse first = idempotencyService.execute(userId, key, new SendRequest("sig_12"),
				TimedResponse.class, () -> new TimedResponse("msg_1", requestedAt, null));

		TimedResponse retried = idempotencyService.execute(userId, key, new SendRequest("sig_12"),
				TimedResponse.class, () -> {
					throw new IllegalStateException("재시도에서 작업이 다시 돌면 안 된다");
				});

		assertThat(retried).isEqualTo(first);
		assertThat(retried.requestedAt()).isEqualTo(requestedAt);
		// 값이 없던 시각은 null로 돌아온다
		assertThat(retried.deliveredAt()).isNull();
	}

	@Test
	void theFirstRequestRunsTheAction() {
		SendResponse response = send(key, new SendRequest("sig_12"));

		assertThat(runs).hasValue(1);
		assertThat(response.id()).isEqualTo("msg_1");
	}

	@Test
	void retryingWithTheSameKeyReturnsTheFirstResponseWithoutRunningAgain() {
		SendRequest request = new SendRequest("sig_12");
		SendResponse first = send(key, request);

		SendResponse retried = send(key, request);

		// 신호가 두 번 울리면 안 된다
		assertThat(runs).hasValue(1);
		assertThat(retried).isEqualTo(first);
		assertThat(retried.rhythm()).containsExactly("SHORT", "LONG");
	}

	@Test
	void reusingTheKeyForADifferentRequestIsRejected() {
		send(key, new SendRequest("sig_12"));

		assertThatThrownBy(() -> send(key, new SendRequest("sig_99")))
				.isInstanceOf(SonulimException.class)
				.extracting(thrown -> ((SonulimException) thrown).getErrorCode())
				.isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
		assertThat(runs).hasValue(1);
	}

	@Test
	void theSameKeyFromAnotherUserIsUnrelated() {
		SendRequest request = new SendRequest("sig_12");
		send(key, request);

		SendResponse other = idempotencyService.execute(UUID.randomUUID(), key, request, SendResponse.class,
				() -> new SendResponse("msg_other", "REQUESTED", List.of("SHORT")));

		assertThat(other.id()).isEqualTo("msg_other");
	}

	@Test
	void aMissingKeyIsRejected() {
		assertThatThrownBy(() -> send(null, new SendRequest("sig_12")))
				.isInstanceOf(SonulimException.class)
				.extracting(thrown -> ((SonulimException) thrown).getErrorCode())
				.isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
		assertThat(runs).hasValue(0);
	}

	@Test
	void aKeyThatIsNotAUuidIsRejectedAsAValidationError() {
		assertThatThrownBy(() -> send("not-a-uuid", new SendRequest("sig_12")))
				.isInstanceOf(SonulimException.class)
				.satisfies(thrown -> {
					SonulimException exception = (SonulimException) thrown;
					assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
					assertThat(exception.getField()).isEqualTo("Idempotency-Key");
				});
	}

	@Test
	void recordsPastTheRetentionWindowAreRemovedSoTheKeyCanBeUsedAgain() {
		send(key, new SendRequest("sig_12"));
		assertThat(repository.count()).isEqualTo(1);

		// 공통 규칙은 24시간 안의 재시도만 같은 응답을 돌려준다 — 그 뒤 기록은 지운다
		int deleted = transactionTemplate.execute(status ->
				repository.deleteOlderThan(LocalDateTime.now().plusDays(2)));

		assertThat(deleted).isEqualTo(1);
		assertThat(repository.count()).isZero();
	}

	@Test
	void aFailedActionFreesTheKeySoTheAppCanRetry() {
		assertThatThrownBy(() -> idempotencyService.execute(userId, key, new SendRequest("sig_12"),
				SendResponse.class, () -> {
					throw new IllegalStateException("푸시 발송 실패");
				})).isInstanceOf(IllegalStateException.class);

		// 자리까지 함께 롤백되어야 한다 — 남아 있으면 그 키가 영영 막혀 다시 보낼 수 없다
		assertThat(repository.findByUserIdAndKey(userId, UUID.fromString(key))).isEmpty();

		SendResponse afterRetry = send(key, new SendRequest("sig_12"));
		assertThat(runs).hasValue(1);
		assertThat(afterRetry.id()).isEqualTo("msg_1");
	}
}
