package com.sonulim.domain.signal.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RhythmPresetRepository extends JpaRepository<RhythmPreset, UUID> {
}
