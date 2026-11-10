package com.sonulim.domain.signal.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalTemplateRepository extends JpaRepository<SignalTemplate, UUID> {
}
