package com.sonulim.domain.location.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.sonulim.domain.location.domain.LocationFailureReason;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;

/**
 * 당사자 기기가 위치를 보내거나, 못 얻었으면 그 사유를 보낸다(위치 보내기 API).
 *
 * @param error 권한 거부·측위 실패. 이 값이 있으면 좌표는 보지 않는다
 */
public record FulfillLocationRequest(BigDecimal latitude, BigDecimal longitude, Integer accuracyM,
		LocalDateTime capturedAt, LocationFailureReason error) {

	boolean isFailure() {
		return error != null;
	}

	/** 실패 사유가 없으면 위치를 보낸 것이므로 좌표가 있어야 한다. */
	void requireComplete() {
		if (isFailure()) {
			return;
		}
		requirePresent(latitude, "latitude", "위도가 필요해요");
		requirePresent(longitude, "longitude", "경도가 필요해요");
		requirePresent(capturedAt, "capturedAt", "위치를 잰 시각이 필요해요");
	}

	private static void requirePresent(Object value, String field, String message) {
		if (value == null) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED, message, field);
		}
	}
}
