package com.sonulim.domain.signal.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteSignalRepository extends JpaRepository<FavoriteSignal, UUID> {

	List<FavoriteSignal> findByLinkIdOrderByPosition(UUID linkId);

	void deleteByLinkId(UUID linkId);
}
