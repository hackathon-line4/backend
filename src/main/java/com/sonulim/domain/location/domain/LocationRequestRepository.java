package com.sonulim.domain.location.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRequestRepository extends JpaRepository<LocationRequest, UUID> {

	/**
	 * 이 연결에서 아직 응답을 기다리는 요청. 진행 중인 요청이 있으면 새로 만들지 않고 그것을 돌려준다
	 * (위치 새로고침 요청 API).
	 */
	Optional<LocationRequest> findFirstByLinkIdAndStatusOrderByRequestedAtDesc(UUID linkId,
			LocationRequestStatus status);

	/** 제한 시간이 지났는데 아직 응답이 없는 요청. */
	List<LocationRequest> findByStatusAndTimeoutAtLessThanEqual(LocationRequestStatus status, LocalDateTime at);
}
