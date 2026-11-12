package com.sonulim.domain.account.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

// 정리용 삭제는 바로 실행되는 벌크 쿼리로 한다 — Hibernate는 flush 때 insert를 delete보다 먼저 보내서,
// 엔티티 삭제로 하면 같은 push_token을 가진 새 행이 unique 제약에 걸린다.
public interface DeviceRepository extends JpaRepository<Device, String> {

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from Device d where d.pushToken = :pushToken and d.installationId <> :installationId")
	int deleteOtherDevicesWithPushToken(String pushToken, String installationId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from Device d where d.userId = :userId and d.installationId <> :installationId")
	int deleteOtherDevicesOfUser(UUID userId, String installationId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from Device d where d.userId = :userId and d.installationId = :installationId")
	int deleteDeviceOfUser(UUID userId, String installationId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Device d set d.lastSeenAt = :now where d.userId = :userId")
	int updateLastSeenAt(UUID userId, LocalDateTime now);

	@Query("select d.pushToken from Device d where d.userId = :userId")
	List<String> findPushTokensByUserId(UUID userId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from Device d where d.pushToken = :pushToken")
	int deleteByPushToken(String pushToken);
}
