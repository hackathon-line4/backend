package com.sonulim.domain.vibration.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VibrationSettingsRepository extends JpaRepository<VibrationSettings, UUID> {
}
