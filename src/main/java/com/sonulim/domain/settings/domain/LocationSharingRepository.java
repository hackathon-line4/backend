package com.sonulim.domain.settings.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationSharingRepository extends JpaRepository<LocationSharing, UUID> {
}
