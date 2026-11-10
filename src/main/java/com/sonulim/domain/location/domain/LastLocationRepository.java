package com.sonulim.domain.location.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LastLocationRepository extends JpaRepository<LastLocation, UUID> {
}
