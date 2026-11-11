package com.sonulim.domain.location.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.sonulim.domain.location.application.LocationViewService;
import com.sonulim.domain.location.domain.LastLocation;

/**
 * 위치 탭과 홈 '자세히 보기'(마지막 위치 조회 API).
 *
 * @param last 공유가 꺼져 있으면 항상 null
 */
public record LocationViewResponse(boolean sharingEnabled, Last last, LocationRequestResponse pendingRequest) {

	/** address는 주소 변환이 끝난 뒤 채워지므로 null일 수 있다. */
	public record Last(BigDecimal latitude, BigDecimal longitude, Integer accuracyM, String address,
			LocalDateTime capturedAt) {

		public static Last from(LastLocation location) {
			return location == null ? null : new Last(location.getLatitude(), location.getLongitude(),
					location.getAccuracyM(), location.getAddress(), location.getCapturedAt());
		}
	}

	static LocationViewResponse from(LocationViewService.LocationView view) {
		return new LocationViewResponse(view.sharingEnabled(), Last.from(view.last()),
				view.pendingRequest() == null ? null : LocationRequestResponse.from(view.pendingRequest()));
	}
}
