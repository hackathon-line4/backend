package com.sonulim.domain.account.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.domain.DeviceRepository;
import com.sonulim.global.push.PushTokenRegistry;

/**
 * FCM 발송(global.push)에 기기 푸시 토큰을 내준다. 발송은 호출한 쪽 커밋 뒤에 일어날 수 있어서
 * 항상 새 트랜잭션에서 실행한다 — 커밋이 끝난 트랜잭션에 참여하면 삭제가 반영되지 않는다.
 */
@Component
public class DevicePushTokenRegistry implements PushTokenRegistry {

	private final DeviceRepository deviceRepository;

	public DevicePushTokenRegistry(DeviceRepository deviceRepository) {
		this.deviceRepository = deviceRepository;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public List<String> tokensOf(UUID userId) {
		return deviceRepository.findPushTokensByUserId(userId);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void discard(String pushToken) {
		deviceRepository.deleteByPushToken(pushToken);
	}
}
