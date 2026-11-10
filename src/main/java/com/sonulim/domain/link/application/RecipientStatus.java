package com.sonulim.domain.link.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import com.sonulim.domain.history.domain.HistoryItem;
import com.sonulim.domain.history.domain.ResponseState;

/**
 * 홈 요약의 당사자 상태(S-4, #81). 명세는 값 목록과 우선순위(HELP_REQUESTED > NO_RESPONSE > WAITING)만 있어서
 * 아래처럼 정했다(docs/domains/link.md Pending Decisions "홈 요약 해석" — 프론트 확인 필요).
 *
 * <ul>
 *   <li>보호자가 아직 열어 보지 않은 도움 요청이 있으면 HELP_REQUESTED(가장 최근 것).</li>
 *   <li>아니면 가장 최근 보호자 신호(연습 제외) 1건의 터치 응답이 NO_RESPONSE·WAITING이면 그 값.</li>
 *   <li>그 밖(기록 없음, 응답함, 전달 중·전달 실패·전달 확인 안 됨)은 OK — 나머지 필드는 null.</li>
 * </ul>
 * since는 신호를 보낸 시각·도움 요청을 보낸 시각, elapsedMinutes는 그때부터 지금까지 분(내림).
 */
public record RecipientStatus(Type type, UUID messageId, String signalName, LocalDateTime since,
		Long elapsedMinutes) {

	public enum Type {
		OK,
		WAITING,
		NO_RESPONSE,
		HELP_REQUESTED
	}

	static final RecipientStatus OK = new RecipientStatus(Type.OK, null, null, null, null);

	/**
	 * @param unopenedHelpRequest 보호자가 열어 보지 않은 가장 최근 도움 요청, 없으면 null
	 * @param latestGuardianSignal 가장 최근 보호자 신호(연습 제외), 없으면 null
	 */
	static RecipientStatus decide(HistoryItem unopenedHelpRequest, HistoryItem latestGuardianSignal,
			LocalDateTime now) {
		if (unopenedHelpRequest != null) {
			return of(Type.HELP_REQUESTED, unopenedHelpRequest, now);
		}
		if (latestGuardianSignal == null || latestGuardianSignal.responseStatus() == null) {
			return OK;
		}
		if (latestGuardianSignal.responseStatus() == ResponseState.NO_RESPONSE) {
			return of(Type.NO_RESPONSE, latestGuardianSignal, now);
		}
		if (latestGuardianSignal.responseStatus() == ResponseState.WAITING) {
			return of(Type.WAITING, latestGuardianSignal, now);
		}
		return OK;
	}

	private static RecipientStatus of(Type type, HistoryItem item, LocalDateTime now) {
		long elapsed = Math.max(0, Duration.between(item.occurredAt(), now).toMinutes());
		return new RecipientStatus(type, item.id(), item.title(), item.occurredAt(), elapsed);
	}
}
