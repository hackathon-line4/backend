package com.sonulim.domain.settings.application;

import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.application.RoleSelectedEvent;
import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.settings.domain.NotificationSettings;
import com.sonulim.domain.settings.domain.NotificationSettingsRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.security.AuthenticatedUser;

@Service
public class NotificationSettingsService {

	private final NotificationSettingsRepository notificationSettingsRepository;

	public NotificationSettingsService(NotificationSettingsRepository notificationSettingsRepository) {
		this.notificationSettingsRepository = notificationSettingsRepository;
	}

	/**
	 * 저장된 값이 없으면(기본값 생성 전에 역할을 고른 계정 등) 기본값을 돌려준다 — 조회만으로는 저장하지 않는다.
	 */
	public NotificationSettings get(AuthenticatedUser user) {
		RoleRequirement.require(user, Role.GUARDIAN);
		return notificationSettingsRepository.findById(user.userId())
				.orElseGet(() -> NotificationSettings.defaults(user.userId()));
	}

	/**
	 * 다른 도메인이 보호자의 알림 설정을 읽는 창구(#93). messaging이 "이 보호자가 알림을 켜 뒀는가"를
	 * 알아야 하는데, {@link #get(AuthenticatedUser)}은 본인 조회라 다른 사람 설정을 볼 수 없다.
	 *
	 * <p>권한은 부르는 쪽이 이미 확인했으므로 여기서 다시 묻지 않는다.
	 * 저장된 값이 없으면 기본값으로 본다 — 역할을 고르면 기본값이 만들어지므로 보통은 있다.
	 */
	public NotificationSettings findFor(UUID userId) {
		return notificationSettingsRepository.findById(userId)
				.orElseGet(() -> NotificationSettings.defaults(userId));
	}

	/**
	 * 보호자 역할을 고르면 기본값(켬, 켬, 10분)을 만든다. selectRole과 같은 트랜잭션에서 실행된다.
	 */
	@EventListener
	public void createDefaultsForGuardian(RoleSelectedEvent event) {
		if (event.role() == Role.GUARDIAN && !notificationSettingsRepository.existsById(event.userId())) {
			notificationSettingsRepository.save(NotificationSettings.defaults(event.userId()));
		}
	}

	/**
	 * 보낸 값만 바꾼다. 바꾼 대기 시간은 새로 기기에 전달되는 신호부터 적용된다(판정은 messaging 쪽).
	 */
	@Transactional
	public NotificationSettings update(AuthenticatedUser user, NotificationSettingsChange change) {
		RoleRequirement.require(user, Role.GUARDIAN);
		if (change.helpRequestAlertSent()) {
			throw SonulimException.forField(ErrorCode.FIELD_READ_ONLY, "도움 요청 알림은 끌 수 없어요", "helpRequestAlert");
		}
		if (change.responseWaitMinutes() != null
				&& !NotificationSettings.ALLOWED_RESPONSE_WAIT_MINUTES.contains(change.responseWaitMinutes())) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED,
					"응답 기다리는 시간은 5, 10, 30분 중 하나여야 해요", "responseWaitMinutes");
		}

		NotificationSettings settings = notificationSettingsRepository.findById(user.userId())
				.orElseGet(() -> notificationSettingsRepository.save(NotificationSettings.defaults(user.userId())));
		settings.change(change.touchResponseAlert(), change.noResponseAlert(), change.responseWaitMinutes());
		return settings;
	}
}
