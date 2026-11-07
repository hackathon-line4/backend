package com.sonulim.domain.recipient.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientSignalRepository extends JpaRepository<RecipientSignal, UUID> {
}
