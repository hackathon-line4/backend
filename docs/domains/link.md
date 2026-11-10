# link — 계정 연결

**담당:** 최은솔
**범위:** 연결 코드/QR 만들기(L-1), 연결 요청·승인·거절·취소(L-2~L-3), 연결 해제(L-4), 연결 상태 확인(T-2, 예외로 이 도메인에 포함), 홈 요약(S-4, 예외로 이 도메인에 포함)

## 현재 상태

- **연결 코드 (#35)** — 만들기·현재 코드 조회(L-1), 코드 확인(L-2 `verify`) 구현. 요청·응답·오류는 Notion API 명세 기준.
  - `LinkCode`(`link_codes`): 6자리 숫자, 만든 뒤 10분 유효. 새로 만들면 그 보호자의 살아 있는 이전 코드는 `revokedAt`으로 무효화한다. 이미 만료된 코드는 건드리지 않는다(확인 시 `LINK_CODE_EXPIRED`로 구분하기 위해).
  - 살아 있는 코드(무효화 안 됨 + 만료 전)끼리 값이 겹치지 않게, 만들 때 `SecureRandom`으로 뽑아 겹치면 다시 뽑는다(최대 10회, 넘으면 `INTERNAL_ERROR`). 만료가 시간 기준이라 DB 유니크 제약으로는 걸지 못한다 — 동시에 같은 값이 나올 가능성은 남지만 100만 가지 중이라 무시한다.
  - `Idempotency-Key`: 공통 처리가 아직 없어서 이 API 안에서만 처리한다. `(guardian_id, idempotency_key)` 유니크, 같은 키로 다시 오면 그때 만든 코드를 그대로 201로 돌려준다.
  - 코드 확인 무작위 대입 방지: 사용자당 첫 실패부터 10분 구간에 10회 틀리면 그 구간이 끝날 때까지 `TOO_MANY_ATTEMPTS`. 없는 코드·무효화된 코드(`LINK_CODE_NOT_FOUND`)와 만료된 코드(`LINK_CODE_EXPIRED`)를 모두 틀린 시도로 센다. 형식 오류(400)는 세지 않는다. 서버 메모리에 저장(재시작 시 초기화, 서버 2대 이상이면 공유 저장소 필요).
  - `usedAt`(사용 완료)·`ALREADY_LINKED` 검사는 #37에서 추가했다(아래).
- **연결 요청 흐름 (#37)** — 요청 보내기·취소·조회(L-2), 받은 요청 목록·승인·거절(L-3) 구현. 요청·응답·오류는 Notion API 명세 기준.
  - `LinkRequest`(`link_requests`): `PENDING` → `APPROVED`/`REJECTED`/`CANCELED`/`EXPIRED`. 요청한 뒤 10분 유효. `PENDING`이 아닌 요청을 바꾸려 하면 409 `LINK_REQUEST_NOT_PENDING`(`details.status`에 현재 상태).
  - 만료는 서버 타이머 없이 요청을 읽거나 바꿀 때 판정한다(조회·목록·취소·승인·거절·요청 보내기). 명세의 "조회 시점에 `EXPIRED`로 바꿔 돌려준다"를 모든 경로에 적용한 것이다.
  - 요청 보내기의 코드 검사는 코드 확인과 같은 코드(`LinkCodeChecker`)를 쓴다 — 같은 오류 코드, 같은 무작위 대입 시도 제한. 같은 당사자의 이전 `PENDING` 요청은 `CANCELED`로 바꾼다. `Idempotency-Key`는 연결 코드와 같은 방식(`(recipient_id, idempotency_key)` 유니크).
  - 승인은 한 트랜잭션에서 요청 확인 → `Link` 생성 → 요청 `APPROVED` → 코드 `usedAt`. 거절은 코드를 그대로 둔다(만료 전이면 다른 당사자가 다시 쓸 수 있음).
  - 승인·거절·취소는 요청 행을 비관적 락으로 읽어, 동시에 와도 하나만 상태를 바꾼다.
  - 조회는 요청의 보호자·당사자만, 승인·거절은 그 요청의 보호자만, 취소는 그 요청의 당사자만. 남의 요청과 형식이 틀린 id는 없는 요청과 같은 404 `NOT_FOUND`.
  - 목록: 내가 보호자인 요청만 최신순. `status`가 있으면 그 상태 전체, 없으면 최근 24시간 전체. 잘못된 `status`는 400 `VALIDATION_FAILED`(`field=status`).
  - `Link`(`links`): 지금은 `ACTIVE`만 있다. 응답의 `connection`은 연결 상태 확인(T-2) 전이라 항상 `{state: "UNKNOWN", lastConfirmedAt: null}`.
  - MVP 1:1 — `Link.activeGuardianId`·`activeRecipientId`는 살아 있는 연결에만 값이 있는 nullable 유니크 컬럼이라 DB가 한 사람당 살아 있는 연결 하나만 허용한다. 승인이 동시에 와서 제약에 걸리면 `ALREADY_LINKED`. 연결 해제(L-4)에서 두 컬럼을 비운다.
  - `ALREADY_LINKED`(409): 연결된 보호자는 코드 만들기(L-1) 불가. 코드 확인·요청 보내기는 코드의 보호자나 당사자가 이미 연결돼 있으면(코드가 맞을 때만, 틀린 시도로 세지 않음), 승인은 그사이 다른 연결이 생겼으면.

### 후속 작업 (해당 기능 구현 시)

- 실시간·푸시(SSE 허브, 기기 등록 구현 시) — 명세에 적힌 알림을 붙인다. 앱은 SSE가 끊기면 요청 조회를 5초 간격으로 부르므로 그동안에도 결과는 확인된다.
  - 요청 보내기: 보호자에게 `LINK_REQUEST` 푸시 + `link_request.created`. 밀려난 이전 요청의 `link_request.updated`도 함께 검토.
  - 취소: 보호자에게 `link_request.updated`.
  - 승인·거절: 당사자에게 `LINK_RESULT` 푸시 + `link_request.updated`.
  - 만료: 당사자에게 `LINK_RESULT` 푸시 + `link_request.updated` — 이걸 보내려면 만료를 정해진 시각에 처리하는 서버 타이머가 필요하다. 스케줄링 설정(`global.SchedulingConfig`)은 messaging PR #21에 있으므로 그 뒤에 붙인다.
- 승인 시 기본 신호 세트 복사·자주 쓰는 신호 6개 채우기 (`signal` 도메인 구현 시, Pending Decisions 참고).
- account `GET /me`·로그인 응답의 `links`·`pendingLinkRequest` 채우기 — account 코드라 담당자(최용주)와 함께 진행.

## Pending Decisions

- **연결 확인 타임아웃**: 30초(백엔드 제안). — 프론트 확인 필요.
- **기본 신호 세트**: 연결 승인 시(`POST /link-requests/{id}/approve`) 이 연결의 신호 목록에 기본 신호를 자동 추가하고 자주 쓰는 신호 6개를 채운다(명세에 "협의"로 표시) — 실제 추가 로직은 `signal` 도메인과 조율 필요. #37 승인에는 아직 넣지 않았다.
- **연결 해제 뒤 기록**: 기록은 남기되 조회 불가, 보관 기간 뒤 삭제(기획 확정 필요) — `history` 도메인과 보관 정책 공유.
- **연결 인원 제약 (보호자 : 당사자)**: 자료끼리 맞지 않는다. Notion API 명세(L-1·L-2 "MVP 1:1이므로 `ALREADY_LINKED`")와 이 문서의 이전 기록("1:1(MVP), 경로는 `/links/{linkId}/…`로 둬서 추후 확장 — 이미 확정")은 1:1, Figma 메모는 보호자 한 명이 당사자 여러 명과 연결 가능. #37은 1:1(MVP)로 구현했다. 1:N으로 바뀌면 `Link.activeGuardianId` 유니크 컬럼과 보호자 쪽 `ALREADY_LINKED` 검사(코드 만들기·코드 확인·요청 보내기·승인)를 뺀다 — Figma 메모의 1:N은 팀 확인 필요.
- **연결 요청 10분 경과 처리**: Figma 메모는 "요청 후 10분 동안 응답이 없으면 자동 취소", Notion API 명세는 "지나면 `EXPIRED`". 앱 분기(`REJECTED`·`EXPIRED` → 실패 안내)가 Notion 명세에 있어서 `EXPIRED`로 구현했다(`CANCELED`는 당사자가 취소했거나 새 요청에 밀려난 경우만). — 기획 확인 필요.
- **만료된 요청의 `decidedAt`**: 명세 예시는 승인·거절·취소에만 `decidedAt`이 있고 `EXPIRED` 예시가 없다. 지금은 사람이 정한 것이 아니라서 `null`로 둔다. — 프론트 확인 필요.
- **코드 확인 잠금 시간**: 명세는 "10분에 10회 틀리면 `TOO_MANY_ATTEMPTS`"만 적혀 있고 잠금 시간이 없다. 지금은 첫 실패부터 10분 구간이 끝날 때까지 막는다(명세 예시 `retryAfterSec: 300`과 맞음). 성공해도 실패 횟수를 지우지 않는다.
- **QR 스킴 철자**: 명세의 `qrPayload`가 `sonullim://link?code=…`(l 두 개)로 프로젝트 이름(`sonulim`)과 다르다. 명세 그대로 구현했고, 프론트 딥링크 스킴과 맞는지 확인 필요.
- **id 형식**: 명세 예시는 `"usr_1"`·`"lrq_7"`·`"lnk_1"`이지만 다른 API와 같이 UUID로 내려준다.
