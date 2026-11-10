# messaging — 보호자→당사자 신호 전송

**담당:** 조한결
**범위:** 신호 보내기(S-1, S-2), 응답 없음 판정(S-3), 수신 확인(ack)·진동 재생 보고·터치 응답(R-1 중 수신 태그), 더 기다리기, 다시 보내기, 신호 기록 상세(예외로 이 도메인에 포함)

## 현재 구현

- 신호 기록 도메인 모델 `messages` / `message_events` (#18). API 레이어는 `links`·`signals` 구현 후
- 신호 한 건은 **기기 전달**(`delivery`)과 **터치 응답**(`response`) 두 상태를 따로 가진다. 상태 변경은 전부 `Message`의 메서드 안에서만 일어난다 — 한 축의 전이가 다른 축을 덮지 않게 하기 위해서다
- 전달 완료는 당사자 앱의 ack로만 바꾼다. 푸시 발송 성공으로는 바꾸지 않는다
  - ack 없이 터치가 먼저 오면 ack도 같은 시각으로 함께 기록한다 — 타임라인에서 '기기에 도착'이 빠지지 않게
  - `UNCONFIRMED` 뒤에 늦게 온 ack도 `DELIVERED`로 받아들이고 `unconfirmedAt`은 남긴다
- 진동 재생 보고는 누적 횟수라 **지금 값보다 큰 값만** 받아들인다. 보고가 순서 뒤바뀌어 도착해도 횟수가 줄지 않는다
- '더 기다리기'는 `noResponseAt`을 지우지 않고 `WAITING`으로 되돌린다 — 판정이 한 번 있었다는 기록을 남긴다
- 응답 없음 판정은 이미 `RESPONDED`인 기록을 덮지 않는다. 판정 타이머와 터치가 동시에 들어와도 응답이 이긴다
- 스냅샷은 `rhythm`과 **`pulses`를 함께** 담는다 (#73). 기본 신호는 불규칙한 쉼이 있어 `SHORT/MEDIUM/LONG`으로 담기지 않아 `rhythm`이 **null일 수 있고**, 기기는 `pulses`(`[울림, 쉼, 울림, …]` ms)를 보고 울린다 — `pulses`가 비면 기본 신호를 받은 당사자 기기가 진동하지 않는다
- 신호 값은 보낸 시점 스냅샷(`signal_snapshot`, jsonb)으로 박제한다. 신호를 고치거나 지워도 과거 기록의 이름·리듬은 그대로다
  - 스냅샷 record에 `isXxx()`/`getXxx()` 파생 접근자를 두지 않는다 — Jackson이 JSON 필드로 써 버려서 되읽을 때 깨진다. 긴급 여부 판정은 `Message`가 한다
- `users`/`links`/`signals`는 FK 없이 UUID 값으로만 참조한다(docs/architecture.md: 도메인 패키지는 서로 직접 의존하지 않는다)
- 기록 상세 타임라인은 `Message`가 상태를 바꿀 때 직접 남긴다 — 서비스가 따로 기록하면 전이를 추가할 때 빠뜨린다
  - step 9개: `SENT` `FAILED` `DELIVERED` `UNCONFIRMED` `PLAYED` `RESPONDED` `NO_RESPONSE` `WAIT_EXTENDED` `EMERGENCY_WARNING`
  - `PLAYED`는 줄을 하나만 두고 `meta.count`만 갱신한다(명세: "처음 보고 때 PLAYED를 남기고 이후에는 횟수만 갱신")
- ack 판정 시간·응답 대기 시간은 아직 확정되지 않아 상수로 박지 않고 메서드 파라미터로 받는다
- 서버 타이머 3종 (#20): ack 판정 → `UNCONFIRMED`, 대기 만료 → `NO_RESPONSE`, 긴급 전달 후 무응답 → `emergencyWarning`
  - 기한(`ackDeadline`·`waitUntil`)을 DB 컬럼으로 들고 있어 **서버가 재시작해도 판정이 사라지지 않는다**. 짧은 주기(`sonulim.messaging.timer-sweep-interval-ms`, 기본 5초) 스케줄러가 지난 건을 훑는다
  - 판정과 터치 응답의 경합은 **낙관적 락(`@Version`)**으로 푼다. 벌크 UPDATE가 아니라 엔티티를 거치는 이유는 판정도 타임라인에 남아야 하기 때문이다 — 진 쪽은 `ObjectOptimisticLockingFailureException`으로 끝나고 타이머가 그 건을 건너뛴다. 늦게 도착한 터치가 판정에 덮이지 않는다
  - 판정은 건별 트랜잭션이라 한 건이 실패해도 나머지가 함께 말리지 않는다
  - `@Scheduled`의 `fixedDelayString`은 `5s` 같은 표기를 못 읽는다(밀리초 또는 ISO-8601만). 그래서 주기만 ms 숫자로 두고, 미확정 **업무** 값인 긴급 경고 지연은 `MessagingProperties`에 Duration으로 둔다
- 당사자 수신 보고 3종 (#22): `POST /messages/{id}/ack` · `/playback` · `/response`. 당사자(받는 사람)만 호출
  - 역할이 다르면 403 `ROLE_MISMATCH`, 역할 선택 전이면 403 `ROLE_REQUIRED`, **내 기록이 아니면 404** — 남의 기록이 존재한다는 사실 자체를 숨긴다(공통 규칙의 연결 범위)
  - 셋 다 재시도에 안전하다. 당사자 앱이 실패 시 자동으로 다시 보내기 때문에 같은 요청이 여러 번 와도 결과가 달라지지 않는다
  - 응답 대기 시간은 보호자가 고른 값(`notification_settings`)을 써야 하지만, settings 도메인 구현 전까지 `sonulim.messaging.default-response-wait-minutes`를 쓴다
  - 값이 없는 필드는 빼지 않고 `null`로 둔다(공통 규칙). 테스트에서 `jsonPath(...).doesNotExist()`는 "없음"과 "null"을 **둘 다 통과시키므로** 키 존재를 `hasKey`로 따로 확인한다
- 조회·대기 연장 3종 (#65): `GET /messages/incoming` · `GET /messages/{id}` · `POST /messages/{id}/extend-wait`
  - 받은 신호 조회는 보호자 이름을 `UserQueryService.findAll`로 **한 번에** 읽는다 — 항목마다 조회하면 목록 길이만큼 쿼리가 늘어난다
  - 기록 상세는 `findWithTimeline`(fetch join)으로 타임라인을 같이 가져온다
  - `canResend`는 원래 신호가 살아 있는지를 `SignalQueryService.getDetail`로 확인한다. signal 도메인이 조회 실패를 예외로 알려 주므로 그 예외를 false로 받는다 — 삭제 여부만 필요해서다
  - `relatedHelpRequest`는 기준 시간(30분 제안)이 미확정이라 아직 항상 `null`이다
  - 긴급 신호의 재생 정책(10초 반복·최대 60초·방해 금지 무시)은 `sonulim.messaging.emergency-playback` 설정값이다. 일반 신호는 `null`을 내려 앱 기본값을 쓰게 한다
- 역할 검사는 `MessagingRoles`에 모았다 — 역할 미선택은 `ROLE_REQUIRED`, 다른 역할은 `ROLE_MISMATCH`
- 타임라인은 `LAZY`다. 목록 조회에서 N+1이 나지 않게 유지하고, 기록 상세는 fetch join으로 가져온다 — 트랜잭션 밖에서 `getTimeline()`을 읽으면 터진다

### 후속 작업 (해당 기능 구현 시)

- `canResend`: 원래 신호가 삭제되지 않았는지 — `signal` 도메인 구현 후
- 응답에 사용자 이름이 필요한 3개(`extend-wait`·`incoming`·`detail`) — `messages`에는 UUID만 있어 `account`에서 이름을 읽어 와야 한다. `MeService.getMe()`는 사용자가 없으면 401을 던져 제3자 조회용으로는 의미가 맞지 않으므로, account에 읽기 전용 조회를 추가하고 담당자(최용주)를 리뷰어로 지정한다
- 신호 보내기·다시 보내기의 연결 구성원 검증(아니면 403이 아니라 404) — `link` 도메인 구현 후
- 응답 대기 시간을 보호자의 `notification_settings.response_wait_minutes`에서 읽기 — `settings` 구현 후
- `message.created`/`message.updated` 이벤트 발행과 푸시 — 실시간·푸시 구현 후

## Pending Decisions

- **ack 판정 시간**: 60초(백엔드 제안). — 프론트·기획 확인 필요.
- **당사자 앱이 닫혀 있을 때 리듬 재생**: 데이터 푸시에 리듬·재생 정책을 함께 보냄, 재생 방식은 프론트에서 확정. — 프론트 확인 필요.
- **긴급 신호 값**: 기능명세서 제안표 그대로(10초 반복, 최대 1분, 30초 경고, 방해 금지 무시). — 기획 확인 필요.
- **판정 뒤 들어온 터치**: `RESPONDED` + `late: true`, 보호자에게 알림. — 기획·프론트 확인 필요.
- **'더 기다리기' 때 당사자 기기 재알림**: 다시 울리지 않고 응답만 받음. — 기획 확인 필요.
- **기록 상세의 `relatedHelpRequest`**: 도움 요청 뒤 30분 안에 보낸 신호면 그 요청 요약을 함께 보여 줌(백엔드 제안). 30분이 확정값이 아니라 아직 구현하지 않았고 `related_help_request_id` 컬럼만 잡아 뒀다. — 기획 확인 필요.
- **전송 실패(`FAILED`) 뒤에 들어온 ack**: 명세 상태표가 `FAILED`를 종착으로만 적어 두고 이 경우를 다루지 않는다. 지금은 상태를 바꾸지 않는다(기록은 `FAILED`로 유지). — 기획 확인 필요.
