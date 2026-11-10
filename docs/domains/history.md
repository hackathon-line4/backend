# history — 신호 기록

**담당:** 최은솔
**범위:** 신호 기록 목록(H-1), 기록 있는 날짜(달력). (도움 요청 상세, 신호 기록 상세, 다시 보내기는 예외로 `recipient`/`messaging`에 있음 — docs/conventions.md 참고)

## 현재 상태

- **신호 기록 목록·기록 있는 날짜 (#45)** — `GET /api/v1/links/{linkId}/history`, `GET /api/v1/links/{linkId}/history/calendar`(H-1). 요청·응답·오류는 Notion API 명세와 "공통 데이터 형식"의 HistoryItem 기준.
  - 보호자만(당사자는 403 `ROLE_MISMATCH`, 역할 선택 전은 403 `ROLE_REQUIRED`).
  - `messages`(보호자 신호·연습), `recipient_signals`(보호자 부르기), `help_requests`(도움 요청)를 합쳐 최신순으로, 페이지 나눔 없이 전부(명세). 지금은 기록의 `linkId`가 경로 값이고 `guardianId`가 나인 것만 고른다.
  - 하루는 `tz`(기본 `Asia/Seoul`) 기준 00:00~24:00 — 그 범위를 UTC로 바꿔 읽는다. 응답 시각은 `tz`와 관계없이 `+09:00`(공통 규칙). `tz`는 하루 경계에만 쓴다.
  - `date` 생략 시 `tz` 기준 오늘. 오늘보다 뒤 날짜는 400 `VALIDATION_FAILED`(`field=date`). 형식이 틀린 `date`·`filter`·`tz`도 400(각 `field`).
  - `filter=NO_RESPONSE`면 터치 응답이 `NO_RESPONSE`인 보호자 신호만(명세). 연습은 응답이 없어 들어가지 않는다.
  - 달력: `month`(`YYYY-MM`) 필수(없거나 틀리면 400 `field=month`). 그 달에서 `tz` 기준 날짜별로 묶어 기록이 있는 날만 날짜순. `count`는 그날 전체 기록 수, `hasNoResponse`는 그날 `NO_RESPONSE` 보호자 신호가 하나라도 있으면 `true`. 미래 달은 빈 `days`.
  - **다른 도메인 읽기**: messaging·recipient를 읽는 곳은 `history.infrastructure.JpaHistoryRecords` 하나다. 그쪽 코드는 고치지 않고 엔티티를 JPQL로 읽기만 한다(그쪽 Repository에 기록 조회 메서드가 없음). 나머지 history 계층은 `history.domain.HistoryItem`(자체 읽기 모델)만 쓴다. 상태 enum은 switch로 옮겨서 그쪽에 값이 늘면 컴파일 오류가 난다. `Message`의 타임라인(LAZY)은 읽지 않는다.
  - 역할 확인은 link·settings와 같은 방식으로 `history.application.RoleRequirement`에 둔다(공통화는 팀 결정 후).

### 후속 작업 (해당 기능 구현 시)

- ⚠️ **Link가 dev에 합쳐진 뒤 연결 존재 확인(404)과 `ENDED` 검사를 반드시 추가** — 이 브랜치(#32 위)에 `Link`가 없어서 연결을 확인할 수 없다. 지금은 **없는 연결·남의 연결이 404가 아니라 200 `items: []`(달력은 빈 `days`)**가 되는 임시 처리다(형식이 틀린 `linkId`만 404). 연결의 보호자가 아니면 404, 해제된 연결의 기록은 Pending Decisions("연결 해제 뒤 기록") 확정에 따라 처리한다.
- `canResend`: 원래 신호가 삭제됐으면 `false` — `signal` 도메인 구현 후(messaging 문서와 같은 후속).
- messaging·recipient가 기록 조회 서비스를 내놓으면 `JpaHistoryRecords`를 그 호출로 바꾼다.
- 조회 인덱스: `messages(link_id, guardian_id, requested_at)`, `recipient_signals`·`help_requests(link_id, guardian_id, sent_at)` 인덱스가 없다. 그 도메인 코드라 여기서 추가하지 않았다 — 데이터가 늘면 담당자(조한결)와 함께 추가.
- 하루 200건이 넘으면 커서 방식 검토(명세). 달력은 한 달 기록을 읽어 메모리에서 묶는다 — 많아지면 날짜별 집계 쿼리로 바꾼다.

## Pending Decisions

- **기록 보관 기간·삭제 방법**: 미정(예: 90일 뒤 자동 삭제). — 기획 확인 필요.
- **연결 해제 뒤 기록**: 남기되 조회 불가, 보관 기간 뒤 삭제 — `link` 도메인과 보관 정책 공유. #45 범위에서 뺐다(위 후속 작업).
- **HistoryItem 매핑 (명세 해석, #45)**: 공통 데이터 형식에 값 목록과 예시만 있어서 아래처럼 정했다(팀 확인).
  - `summaryStatus`: 전달 `REQUESTED`→`SENDING`, `FAILED`→`FAILED`, `UNCONFIRMED`→`UNCONFIRMED`. `DELIVERED`면 응답 상태(`WAITING`/`RESPONDED`/`NO_RESPONSE`) 그대로, 응답이 없으면(연습·당사자 기록) `DELIVERED_ONLY`.
  - 보호자 신호의 `delivery.at`: `DELIVERED`→전달 시각, `FAILED`→요청 시각(명세 예시), `UNCONFIRMED`→미확인 판정 시각, `REQUESTED`→`null`.
  - `response.at`: `RESPONDED`→응답 시각, `NO_RESPONSE`→응답 없음 판정 시각(명세 예시), `WAITING`→`null`. 응답이 없는 기록은 `response: null`.
  - 당사자 신호·도움 요청의 `delivery`: 보호자 휴대폰 도착 시각이 있으면 `DELIVERED`(at = 도착 시각), 없으면 `REQUESTED`(→ 배지 `SENDING`). `failureReason`은 항상 `null`.
  - `canResend`: 보호자 신호(연습 제외)가 `FAILED`이거나 `NO_RESPONSE`면 `true`(명세 예시), 나머지(연습·당사자 기록 포함)는 `false`.
  - ⚠️ `canResend`의 **`UNCONFIRMED`(전달 확인 안 됨) → `true`는 명세 예시에 없는 추측**이다. — **프론트 확인 필요.**
  - `title`·`description`·`isEmergency`: 보호자 신호·연습은 보낸 시점 스냅샷의 이름·설명·긴급 여부, 보호자 부르기(`CALL_GUARDIAN`)는 "보호자 부르기"·`false`, 도움 요청은 "도움 요청"·`description: null`·`isEmergency: true`(명세 예시).
- **`hasPrevDate` (명세 해석, #45)**: 정의가 없다. 고른 날짜(`tz` 기준 00:00)보다 이전에 이 연결·이 보호자의 기록이 하나라도 있으면 `true`로 두고, `filter`와 관계없이 판정한다. — 프론트 확인 필요.
