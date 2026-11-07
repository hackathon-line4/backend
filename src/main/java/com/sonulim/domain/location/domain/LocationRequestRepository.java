package com.sonulim.domain.location.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRequestRepository extends JpaRepository<LocationRequest, UUID> {

	/** 제한 시간이 지났는데 아직 응답이 없는 요청. */
	List<LocationRequest> findByStatusAndTimeoutAtLessThanEqual(LocationRequestStatus status, LocalDateTime at);
}
