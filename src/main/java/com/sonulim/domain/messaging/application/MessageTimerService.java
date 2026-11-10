package com.sonulim.domain.messaging.application;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.messaging.domain.Message;
import com.sonulim.domain.messaging.domain.MessageRepository;

/**
 * 기한이 지난 기록 하나를 판정한다. 건별로 트랜잭션을 끊어, 한 건이 실패해도 나머지 판정이 함께 말리지 않게 한다.
 */
@Service
public class MessageTimerService {

	private final MessageRepository messageRepository;

	public MessageTimerService(MessageRepository messageRepository) {
		this.messageRepository = messageRepository;
	}

	/**
	 * 판정을 시도한다. 그 사이에 당사자의 터치나 ack가 먼저 반영됐다면 낙관적 락이 걸려 아무것도 바꾸지 않는다 —
	 * 늦게 도착한 응답이 판정에 덮이지 않아야 하기 때문이다(서버 내부 처리).
	 *
	 * <p>Message의 전이 메서드가 상태를 한 번 더 확인하므로, 읽은 뒤 상태가 바뀐 경우에도 안전하다.
	 *
	 * @return 실제로 판정이 일어났으면 true
	 */
	@Transactional
	public boolean apply(UUID messageId, BiConsumer<Message, LocalDateTime> verdict, LocalDateTime now) {
		Message message = messageRepository.findById(messageId).orElse(null);
		if (message == null) {
			return false;
		}
		try {
			verdict.accept(message, now);
			messageRepository.saveAndFlush(message);
			return true;
		}
		catch (ObjectOptimisticLockingFailureException alreadyChanged) {
			// 응답이 먼저 들어왔다. 판정을 버린다.
			return false;
		}
	}
}
