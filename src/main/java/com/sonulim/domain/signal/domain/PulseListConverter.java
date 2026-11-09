package com.sonulim.domain.signal.domain;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * pulses([울림, 쉼, 울림, …] ms 배열)를 콤마로 구분한 숫자 문자열로 저장한다. 빈/널 리스트는
 * 널 컬럼으로, 널/빈 컬럼은 빈 리스트로 왕복한다. 컬럼에 숫자가 아닌 값이 있으면(데이터 손상)
 * 조용히 무시하지 않고 즉시 실패한다.
 */
@Converter
public class PulseListConverter implements AttributeConverter<List<Integer>, String> {

	private static final String DELIMITER = ",";

	@Override
	public String convertToDatabaseColumn(List<Integer> attribute) {
		if (attribute == null || attribute.isEmpty()) {
			return null;
		}
		return attribute.stream().map(String::valueOf).collect(Collectors.joining(DELIMITER));
	}

	@Override
	public List<Integer> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return List.of();
		}
		return Arrays.stream(dbData.split(DELIMITER))
				.map(Integer::valueOf)
				.toList();
	}
}
