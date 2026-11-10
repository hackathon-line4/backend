package com.sonulim.domain.recipient.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpRequestRepository extends JpaRepository<HelpRequest, UUID> {
}
