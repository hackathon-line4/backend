package com.sonulim.domain.signal.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalRepository extends JpaRepository<Signal, UUID> {

	List<Signal> findByLinkIdAndDeletedAtIsNull(UUID linkId);

	Optional<Signal> findByIdAndDeletedAtIsNull(UUID id);
}
