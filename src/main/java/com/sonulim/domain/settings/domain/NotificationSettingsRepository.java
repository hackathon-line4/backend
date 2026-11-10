package com.sonulim.domain.settings.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, UUID> {
}
