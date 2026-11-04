package com.sonulim.global;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * 공통 규칙: 서버 저장은 UTC, 응답은 +09:00, 초 단위까지 유지(밀리초 이하는 버림).
 * BaseEntity의 createdAt/updatedAt처럼 UTC로 저장된 LocalDateTime 필드에만 적용한다.
 */
public class UtcToSeoulLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {

	private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.of("+09:00");
	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	@Override
	public void serialize(LocalDateTime utcValue, JsonGenerator gen, SerializerProvider serializers) throws IOException {
		OffsetDateTime seoulTime = utcValue.truncatedTo(ChronoUnit.SECONDS)
				.atOffset(ZoneOffset.UTC)
				.withOffsetSameInstant(SEOUL_OFFSET);
		gen.writeString(FORMATTER.format(seoulTime));
	}
}
