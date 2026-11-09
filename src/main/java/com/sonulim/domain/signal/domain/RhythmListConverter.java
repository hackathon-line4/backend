package com.sonulim.domain.signal.domain;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * rhythm을 콤마로 구분한 박자 이름 문자열로 저장한다(예: "SHORT,SHORT,LONG"). 순서와 반복을
 * 그대로 보존한다. 빈/널 리스트는 널 컬럼으로, 널/빈 컬럼은 빈 리스트로 왕복한다. 컬럼에 모르는
 * 토큰이 있으면(데이터 손상) 조용히 무시하지 않고 즉시 실패한다.
 */
@Converter
public class RhythmListConverter implements AttributeConverter<List<RhythmBeat>, String> {

	private static final String DELIMITER = ",";

	@Override
	public String convertToDatabaseColumn(List<RhythmBeat> attribute) {
		if (attribute == null || attribute.isEmpty()) {
			return null;
		}
		return attribute.stream().map(Enum::name).collect(Collectors.joining(DELIMITER));
	}

	@Override
	public List<RhythmBeat> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return List.of();
		}
		return Arrays.stream(dbData.split(DELIMITER))
				.map(RhythmBeat::valueOf)
				.toList();
	}
}
