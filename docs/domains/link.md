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
  - 승인은 한 트랜잭션에서 요청 확인 → `Link` 생성 → 기본 신호 채우기 → 요청 `APPROVED` → 코드 `usedAt`. 거절은 코드를 그대로 둔다(만료 전이면 다른 당사자가 다시 쓸 수 있음).
  - 기본 신호 채우기 (#78): 연결 생성 직후 `global.signalseed.DefaultSignalSeeder.seedDefaults(linkId)`를 부른다. 구현은 signal 도메인(`SignalTemplateSeeder`, #52)이 `signal_templates` 카탈로그 전체를 이 연결의 신호로 복사한다. link는 signal을 직접 import하지 않는다. 같은 트랜잭션이라 기본 신호 채우기가 실패하면 승인 전체가 롤백되고(500 `INTERNAL_ERROR`, 요청은 `PENDING` 그대로), 승인이 실패하면 채운 신호도 남지 않는다.
  - 승인·거절·취소는 요청 행을 비관적 락으로 읽어, 동시에 와도 하나만 상태를 바꾼다.
  - 조회는 요청의 보호자·당사자만, 승인·거절은 그 요청의 보호자만, 취소는 그 요청의 당사자만. 남의 요청과 형식이 틀린 id는 없는 요청과 같은 404 `NOT_FOUND`.
  - 목록: 내가 보호자인 요청만 최신순. `status`가 있으면 그 상태 전체, 없으면 최근 24시간 전체. 잘못된 `status`는 400 `VALIDATION_FAILED`(`field=status`).
  - `Link`(`links`): `ACTIVE` → `ENDED`(#41). 응답의 `connection`은 연결 확인(T-2, #43) 결과 — 확인한 적 없으면 `{state: "UNKNOWN", lastConfirmedAt: null}`.
  - MVP 1:1 — `Link.activeGuardianId`·`activeRecipientId`는 살아 있는 연결에만 값이 있는 nullable 유니크 컬럼이라 DB가 한 사람당 살아 있는 연결 하나만 허용한다. 승인이 동시에 와서 제약에 걸리면 `ALREADY_LINKED`. 연결 해제(L-4)에서 두 컬럼을 비운다.
  - `ALREADY_LINKED`(409): 연결된 보호자는 코드 만들기(L-1) 불가. 코드 확인·요청 보내기는 코드의 보호자나 당사자가 이미 연결돼 있으면(코드가 맞을 때만, 틀린 시도로 세지 않음), 승인은 그사이 다른 연결이 생겼으면.
- **연결 해제 (#41)** — `DELETE /api/v1/links/{linkId}`(L-4) 구현. 요청·응답·오류는 Notion API 명세 기준.
  - 보호자만(당사자는 403 `ROLE_MISMATCH`). 그 연결의 보호자가 아니거나 없는·형식이 틀린 id는 404 `NOT_FOUND`. 성공하면 204.
  - 연결을 `ENDED`로 바꾸고 `endedAt`을 남긴다(행 삭제 아님). 1:1 유니크 컬럼(`activeGuardianId`·`activeRecipientId`)을 비워 두 사람 모두 다시 연결할 수 있다. 연결 행은 비관적 락으로 읽는다.
  - 이미 해제된 연결을 다시 해제해도 204(아무것도 바꾸지 않음, Pending Decisions).
  - 승인에 쓴 코드(`usedAt` 있음)는 코드 확인·요청 보내기에서 `LINK_CODE_NOT_FOUND`(틀린 시도로 셈). 해제 뒤 만료 전 코드를 다른 당사자가 다시 쓰지 못하게 하려는 것이다(Pending Decisions). 거절된 요청의 코드는 쓰지 않은 코드라 그대로 쓸 수 있다.
  - 해제 뒤 승인된 요청을 조회하면 `link.status`가 `ENDED`로 보인다.
  - **기존 로컬 DB 1회 조치**: Hibernate가 enum 컬럼에 만드는 check 제약은 `ddl-auto: update`가 고치지 않는다. #37로 `links`가 이미 만들어진 DB는 `ENDED` 저장이 실패(500)하므로 아래를 한 번 실행한다. 새로 만드는 DB는 필요 없다.
    ```sql
    ALTER TABLE links DROP CONSTRAINT links_status_check;
    ALTER TABLE links ADD CONSTRAINT links_status_check CHECK (status IN ('ACTIVE', 'ENDED'));
    ```
- **연결 확인 (#43)** — 요청 `POST /api/v1/links/{linkId}/connection-checks`(202), ack `POST /api/v1/connection-checks/{checkId}/ack`(204), 결과 조회 `GET /api/v1/connection-checks/{checkId}`(T-2, 예외로 이 도메인에 포함). 요청·응답·오류는 Notion API 명세 기준.
  - `ConnectionCheck`(`connection_checks`): `PENDING` → `CONFIRMED`/`UNCONFIRMED`. `timeoutAt` = 요청 시각 + 30초(명세 "제안" 값, `ConnectionCheck.TIMEOUT` 상수 하나 — Pending Decisions "연결 확인 타임아웃").
  - 타임아웃은 서버 타이머 없이 요청·ack·조회 시점에 판정한다. 지났는데 `PENDING`이면 `UNCONFIRMED`로 바꾸고 연결도 `UNCONFIRMED`.
  - 요청: 연결의 보호자·당사자(역할 검사 없음). 같은 연결에 아직 끝나지 않은 `PENDING` 확인이 있으면 누가 요청했든 그것을 돌려준다(명세).
  - ack: 요청한 사람이 아닌 쪽만. 처음 한 번만 기록. 타임아웃 안이면 `CONFIRMED`·`confirmedAt`, 연결은 `CONFIRMED`·`lastConfirmedAt`. 타임아웃 뒤의 첫 ack는 연결의 `lastConfirmedAt`만 갱신(명세, 나머지는 Pending Decisions "명세 해석"). 항상 204.
  - `Link.connectionState`(`CONFIRMED`/`UNCONFIRMED`/`UNKNOWN`, Notion "공통 데이터 형식" 확정 값)·`lastConfirmedAt` 추가. 이 컬럼보다 먼저 만들어진 연결 행이 있는 DB에서도 `ddl-auto: update`가 실패하지 않도록 nullable이고, null은 `UNKNOWN`으로 읽는다. `lastConfirmedAt`은 뒤로 가지 않는다.
  - 404 `NOT_FOUND`: 연결의 구성원이 아니거나, 해제된 연결(Pending Decisions)이거나, 없는·형식이 틀린 id.
  - 잠그는 순서는 항상 연결 → 확인(비관적 락). 요청이 동시에 와도 한 연결에 `PENDING`은 하나, ack가 동시에 와도 처음 한 번만 기록.
- **연결 접근 검증·연결 구성원 조회 (#75)** — 다른 도메인이 쓰는 계약 `global.linkaccess.LinkAccessGuard`의 실제 구현 `LinkMembershipGuard`. 모든 프로파일에서 이것 하나만 등록된다(local/test 통과용 구현 `DevelopmentLinkAccessGuard`는 삭제 — prod 기동 실패도 없어졌다).
  - `verifyAccessible(linkId, requesterId)` — 조회·관리 API용. 연결이 없거나 구성원이 아니면 404 `NOT_FOUND`. 해제된(`ENDED`) 연결도 구성원이면 통과(조회·관리 API의 명세 오류 목록에 409가 없음, 팀 결정 #75). 지금 signal 도메인이 쓴다.
  - `requireMembers(linkId, requesterId)` — 전송 API용. 404는 같고, 구성원이지만 `ENDED`면 409 `LINK_ENDED`. 통과하면 `LinkMembers(linkId, guardianId, recipientId)`를 돌려주고 `counterpartOf(userId)`로 상대방 id를 얻는다. messaging·recipient·location 생성 API가 기록을 만들 때 쓰도록 둔 것이다(아직 호출하는 곳 없음). 이름은 사용할 도메인과 맞춰 바꿀 수 있게 `global.linkaccess` 한 곳에 모았다. 사용자 이름은 account의 `UserQueryService`(#48)로 읽는다.
  - 구성원인지 먼저 보고 상태는 그다음에 본다 — 남의 해제된 연결도 404라서 연결이 있었다는 사실이 드러나지 않는다.
  - 연결 확인(#43, 해제된 연결은 404)과 연결 해제(#41, 이미 해제돼도 204)는 각자 규칙이 있어 이 검증을 쓰지 않는다.
  - 테스트: 실제 연결 없이 임의의 linkId로 API를 부르는 테스트는 `PassThroughLinkAccessGuardConfig`(test 소스)를 `@Import`한다(signal API 테스트 5개).

### 후속 작업 (해당 기능 구현 시)

- 실시간·푸시(SSE 허브, 기기 등록 구현 시) — 명세에 적힌 알림을 붙인다. 앱은 SSE가 끊기면 요청 조회를 5초 간격으로 부르므로 그동안에도 결과는 확인된다.
  - 요청 보내기: 보호자에게 `LINK_REQUEST` 푸시 + `link_request.created`. 밀려난 이전 요청의 `link_request.updated`도 함께 검토.
  - 취소: 보호자에게 `link_request.updated`.
  - 승인·거절: 당사자에게 `LINK_RESULT` 푸시 + `link_request.updated`.
  - 만료: 당사자에게 `LINK_RESULT` 푸시 + `link_request.updated` — 이걸 보내려면 만료를 정해진 시각에 처리하는 서버 타이머가 필요하다. 스케줄링 설정(`global.SchedulingConfig`)은 messaging PR #21에 있으므로 그 뒤에 붙인다.
- 승인 시 자주 쓰는 신호 6개 채우기 — 정책 미정(Pending Decisions "기본 신호 세트"). 기본 신호 복사는 #78에서 붙였다.
- account `GET /me`·로그인 응답의 `links`·`pendingLinkRequest` 채우기 — account 코드라 담당자(최용주)와 함께 진행.
- 연결 해제 뒤 처리 (#41에서 뺀 것):
  - 당사자에게 `link.updated` 이벤트(SSE 허브 #39 머지 후).
  - 이 연결로 오는 전송을 `LINK_ENDED`로 막기 — `LinkAccessGuard.requireMembers`가 409를 낸다(#75). 각 생성 API(messaging·recipient·location)가 이것을 부르면 적용된다 — 각 담당자와 조율.
  - 진행 중인 `WAITING` 신호 타이머 멈추기 — messaging 도메인(조한결)과 조율.
- 연결 확인 (#43에서 뺀 것):
  - 상대 기기에 조용한 푸시(`CONNECTION_CHECK`) + `connection_check.requested` 이벤트, ack 뒤 요청한 쪽에 `connection_check.updated` 이벤트(SSE 허브 #39·기기 등록 #17 머지 후).
  - 타임아웃 서버 타이머 — 지금은 아무도 그 확인을 다시 조회하거나 ack하지 않으면 연결의 `connection.state`가 `UNCONFIRMED`로 늦게 바뀐다(앱은 SSE가 없으면 2초마다 조회하므로 실제로는 바로 반영된다). 스케줄러(`global.SchedulingConfig`, messaging PR #21)가 생기면 만료 요청과 함께 붙인다.

## Pending Decisions

- **연결 확인 타임아웃**: 30초(백엔드 제안). — 프론트 확인 필요.
- **기본 신호 세트**: 연결 승인 시(`POST /link-requests/{id}/approve`) 이 연결의 신호 목록에 기본 신호를 자동 추가하고 자주 쓰는 신호 6개를 채운다(명세에 "협의"로 표시) — 기본 신호 추가는 #78에서 붙였다(카탈로그 구성은 signal 도메인 문서). 자주 쓰는 신호 6개 자동 지정은 signal 문서에도 "협의"로 남아 있어 아직 넣지 않았다.
- **연결 해제 뒤 기록**: 기록은 남기되 조회 불가, 보관 기간 뒤 삭제(기획 확정 필요) — `history` 도메인과 보관 정책 공유. #41은 기록을 건드리지 않는다.
- **이미 해제된 연결 다시 해제**: 명세에 없는 경우. 재시도해도 결과(해제됨)가 같으므로 다시 204로 응답한다(팀 결정, #41). — 프론트 확인 필요.
- **사용된 코드 재사용 금지**: 명세에 없는 보수적 결정(팀 결정, #41). 승인에 쓴 코드는 만료 전이어도 코드 확인·요청 보내기에서 `LINK_CODE_NOT_FOUND`. 1:1에서는 `ALREADY_LINKED`로 막혔지만 연결 해제 뒤에는 막을 것이 없어서 넣었다. — 기획 확인 필요.
- **해제된 연결의 연결 확인**: 명세의 연결 확인 3개 API에 해제된 연결 이야기가 없다(오류 목록은 401·404·500뿐). 요청·ack·조회 모두 404 `NOT_FOUND`로 처리한다(팀 결정, #43 — 명세의 오류 목록 안에서만 처리). L-4의 "이후 이 연결로 오는 전송은 `LINK_ENDED`"가 연결 확인도 포함하는지 — 기획 확인 필요.
- **해제된 연결 접근 (#75)**: 전송 API(`requireMembers`)는 409 `LINK_ENDED`(L-4 "이후 이 연결로 오는 전송은 `LINK_ENDED`"), 조회·관리 API(`verifyAccessible`)는 통과(명세 오류 목록에 409 없음) — 팀 결정. 연결 확인만 404(#43, 위 항목). 해제된 연결의 기록 조회는 "연결 해제 뒤 기록" 확정에 따른다.
- **해제와 동시에 온 전송 (#75)**: 연결 접근 검증은 잠금 없이 읽어서, 해제와 거의 같은 순간에 온 전송 하나는 지나갈 수 있다. 막아야 하면 `requireMembers`를 비관적 락으로 읽게 바꾼다 — messaging과 함께 확인 필요.
- **연결 확인 명세 해석 (#43)**: 아래는 명세 문장을 이렇게 해석해 구현했다(팀 확인). — 프론트 확인 필요.
  - "요청한 사람이 아닌 쪽만" ack — 요청한 사람 본인의 ack는 404 `NOT_FOUND`(명세 오류 목록에 404만 있음).
  - 타임아웃 뒤에 온 ack — 확인 결과는 `UNCONFIRMED`·`confirmedAt: null` 그대로, 연결 상태도 `UNCONFIRMED` 그대로 두고 `lastConfirmedAt`만 갱신(명세에 "lastConfirmedAt은 갱신"만 있음).
- **해제 뒤 현재 코드 조회**: 승인에 쓴 코드가 아직 만료 전이면 `GET /link-codes/current`가 그 코드를 돌려주지만, 그 코드는 위 결정으로 쓸 수 없다. 보호자는 새 코드를 만들면 된다. 현재 코드 조회에서도 빼야 할지 — 프론트 확인 필요.
- **연결 인원 제약 (보호자 : 당사자)**: 자료끼리 맞지 않는다. Notion API 명세(L-1·L-2 "MVP 1:1이므로 `ALREADY_LINKED`")와 이 문서의 이전 기록("1:1(MVP), 경로는 `/links/{linkId}/…`로 둬서 추후 확장 — 이미 확정")은 1:1, Figma 메모는 보호자 한 명이 당사자 여러 명과 연결 가능. #37은 1:1(MVP)로 구현했다. 1:N으로 바뀌면 `Link.activeGuardianId` 유니크 컬럼과 보호자 쪽 `ALREADY_LINKED` 검사(코드 만들기·코드 확인·요청 보내기·승인)를 뺀다 — Figma 메모의 1:N은 팀 확인 필요.
- **연결 요청 10분 경과 처리**: Figma 메모는 "요청 후 10분 동안 응답이 없으면 자동 취소", Notion API 명세는 "지나면 `EXPIRED`". 앱 분기(`REJECTED`·`EXPIRED` → 실패 안내)가 Notion 명세에 있어서 `EXPIRED`로 구현했다(`CANCELED`는 당사자가 취소했거나 새 요청에 밀려난 경우만). — 기획 확인 필요.
- **만료된 요청의 `decidedAt`**: 명세 예시는 승인·거절·취소에만 `decidedAt`이 있고 `EXPIRED` 예시가 없다. 지금은 사람이 정한 것이 아니라서 `null`로 둔다. — 프론트 확인 필요.
- **코드 확인 잠금 시간**: 명세는 "10분에 10회 틀리면 `TOO_MANY_ATTEMPTS`"만 적혀 있고 잠금 시간이 없다. 지금은 첫 실패부터 10분 구간이 끝날 때까지 막는다(명세 예시 `retryAfterSec: 300`과 맞음). 성공해도 실패 횟수를 지우지 않는다.
- **QR 스킴 철자**: 명세의 `qrPayload`가 `sonullim://link?code=…`(l 두 개)로 프로젝트 이름(`sonulim`)과 다르다. 명세 그대로 구현했고, 프론트 딥링크 스킴과 맞는지 확인 필요.
- **id 형식**: 명세 예시는 `"usr_1"`·`"lrq_7"`·`"lnk_1"`이지만 다른 API와 같이 UUID로 내려준다.
