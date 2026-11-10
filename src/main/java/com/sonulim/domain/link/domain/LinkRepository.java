package com.sonulim.domain.link.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkRepository extends JpaRepository<Link, UUID> {

	boolean existsByActiveGuardianId(UUID guardianId);

	boolean existsByActiveRecipientId(UUID recipientId);
}
