package com.sonulim.domain.signal.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalRepository extends JpaRepository<Signal, String> {

	List<Signal> findByLinkIdAndDeletedAtIsNull(String linkId);

	Optional<Signal> findByIdAndDeletedAtIsNull(String id);
}
