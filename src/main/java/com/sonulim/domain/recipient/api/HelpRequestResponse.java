package com.sonulim.domain.recipient.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.sonulim.domain.account.application.UserSummary;
import com.sonulim.domain.recipient.domain.CapturedLocation;
import com.sonulim.domain.recipient.domain.HelpRequest;
import com.sonulim.domain.recipient.domain.LocationStatus;

/**
 * 당사자가 보낸 도움 요청 한 건(공통 데이터 형식).
 */
public record HelpRequestResponse(UUID id, UUID linkId, Sender sender, LocalDateTime sentAt,
		LocalDateTime guardianDeliveredAt, LocalDateTime guardianOpenedAt, LocationStatus locationStatus,
		Location location) {

	public record Sender(UUID id, String name) {
	}

	/** address는 주소 변환이 끝난 뒤 채워지므로 null일 수 있다. */
	public record Location(BigDecimal latitude, BigDecimal longitude, Integer accuracyM, String address,
			LocalDateTime capturedAt) {

		static Location from(CapturedLocation location) {
			return location == null ? null : new Location(location.getLatitude(), location.getLongitude(),
					location.getAccuracyM(), location.getAddress(), location.getCapturedAt());
		}
	}

	static HelpRequestResponse of(HelpRequest request, UserSummary sender) {
		return new HelpRequestResponse(request.getId(), request.getLinkId(),
				new Sender(request.getSenderId(), sender == null ? null : sender.name()),
				request.getSentAt(), request.getGuardianDeliveredAt(), request.getGuardianOpenedAt(),
				request.getLocationStatus(), Location.from(request.getLocation()));
	}
}
