package com.sonulim.domain.messaging.domain;

import java.util.List;
import java.util.UUID;

/**
 * 보낸 시점의 신호 값을 통째로 굳힌 것. 신호를 고치거나 지워도 과거 기록의 이름·리듬은 그대로다(공통 데이터 형식).
 * messages.signal_snapshot에 JSON으로 저장한다.
 *
 * @param signalId   원본 신호 ID. 다시 보내기가 이 ID로 현재 값을 다시 읽는다
 * @param rhythm     박자 종류(SHORT/MEDIUM/LONG) 2~5개. 이 도메인에서는 그대로 전달만 하므로 문자열로 둔다.
 *                   <b>기본 신호는 불규칙한 쉼이 있어 박자 종류로 담기지 않으므로 null일 수 있다</b>
 * @param pulses     [울림, 쉼, 울림, …] 순서의 ms 배열. 당사자 기기가 실제로 이걸 보고 울리므로
 *                   리듬이 없는 기본 신호에도 항상 있어야 한다
 * @param durationMs 리듬 전체 길이(= pulses 합). signal 도메인이 계산한 값을 그대로 복사한다
 */
public record SignalSnapshot(
		UUID signalId,
		String name,
		String description,
		SignalCategory category,
		String icon,
		List<String> rhythm,
		List<Integer> pulses,
		int durationMs) {

	// 파생 접근자(isXxx/getXxx)를 두지 않는다 — Jackson이 JSON 필드로 써 버리고,
	// record는 표준 생성자만 받으므로 되읽을 때 깨진다. 긴급 여부 판정은 Message가 한다.
}
