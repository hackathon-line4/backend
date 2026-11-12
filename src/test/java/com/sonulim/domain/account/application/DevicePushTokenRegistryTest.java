package com.sonulim.domain.account.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sonulim.domain.account.domain.Device;
import com.sonulim.domain.account.domain.DeviceRepository;
import com.sonulim.domain.account.domain.Platform;

/**
 * 레지스트리는 새 트랜잭션에서 실행되므로 @Transactional로 묶지 않고 쓴 데이터를 직접 지운다.
 */
@SpringBootTest
class DevicePushTokenRegistryTest {

	@Autowired
	private DevicePushTokenRegistry registry;

	@Autowired
	private DeviceRepository deviceRepository;

	private final UUID userId = UUID.randomUUID();
	private final String installationId = "inst_" + UUID.randomUUID().toString().substring(0, 8);
	private final String pushToken = "push_" + UUID.randomUUID();

	@AfterEach
	void cleanUp() {
		deviceRepository.deleteById(installationId);
	}

	@Test
	void returnsPushTokensOfTheUsersDevices() {
		deviceRepository.save(Device.register(installationId, userId, Platform.ANDROID, pushToken, "1.0.0", true));

		assertThat(registry.tokensOf(userId)).containsExactly(pushToken);
		assertThat(registry.tokensOf(UUID.randomUUID())).isEmpty();
	}

	@Test
	void discardRemovesTheDeviceWithThatToken() {
		deviceRepository.save(Device.register(installationId, userId, Platform.ANDROID, pushToken, "1.0.0", true));

		registry.discard(pushToken);

		assertThat(deviceRepository.findById(installationId)).isEmpty();
	}
}
