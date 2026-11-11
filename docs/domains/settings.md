# settings — 설정

**담당:** 최용주
**범위:** 알림 설정(T-1), 위치 공유 설정(T-3), 당사자 설정 보기. (연결 상태 확인(T-2)은 예외로 `link`에 있음 — docs/conventions.md 참고)

## 현재 구현

- 알림 설정 조회 `GET /api/v1/me/notification-settings`, 변경 `PATCH /api/v1/me/notification-settings` (#10) — 보호자만
  - `notification_settings` 테이블(user_id PK). `helpRequestAlert`는 저장하지 않고 응답에 항상 `true`, 요청에 보내면 400 `FIELD_READ_ONLY`
  - PATCH는 보낸 필드만 변경. `responseWaitMinutes`는 5·10·30만 허용
  - 기본값(터치 응답 알림 켬, 응답 없음 알림 켬, 10분)은 보호자 역할 선택 때 만든다 — account가 발행하는 `RoleSelectedEvent`를 받아 같은 트랜잭션에서 생성. 저장된 값이 없으면 조회는 기본값을 돌려주고, 변경은 기본값을 저장한 뒤 바꾼다
- 위치 공유 설정 조회 `GET /api/v1/me/location-sharing`, 변경 `PUT /api/v1/me/location-sharing` (#12) — 당사자만
  - `location_sharing` 테이블(user_id PK, enabled, changed_at). 응답의 `updatedAt`은 사용자가 마지막으로 바꾼 시각(`changed_at`)이라 한 번도 바꾸지 않았으면 `null`
  - 기본값(꺼짐)은 당사자 역할 선택 때 `RoleSelectedEvent`로 생성
- 다른 도메인이 읽어 가는 창구 2개 — 본인 조회(`get`)로는 남의 설정을 볼 수 없어 따로 뒀다. 권한은 부르는 쪽이 이미 확인했으므로 여기서 다시 묻지 않는다
  - `NotificationSettingsService.findFor(userId)`: messaging이 응답·무응답 푸시를 보낼지 판단할 때 쓴다(#93). 저장된 값이 없으면 기본값으로 본다
  - `LocationSharingService.isSharingEnabled(userId)`: location·도움 요청이 좌표를 저장해도 되는지 볼 때 쓴다. 저장된 값이 없으면 꺼짐으로 본다
- 당사자 설정 보기 `GET /api/v1/links/{linkId}/partner-settings` (#98) — 보호자만
  - 당사자의 진동 설정과 위치 공유 상태를 `{vibration, locationSharing}`으로 묶어 읽기 전용으로 돌려준다. 각 항목 모양은 당사자 본인 조회 응답과 같고, 저장 전이면 기본값
  - 연결 구성원 확인은 공통 `LinkAccessGuard.requireMembers` — 남의 연결·없는 연결·형식이 틀린 id는 404, 해제된 연결은 409 `LINK_ENDED`(가드 동작 그대로)
- 역할 확인은 `settings.application.RoleRequirement`(역할 없음 403 `ROLE_REQUIRED`, 다른 역할 403 `ROLE_MISMATCH`) — 공통화 여부는 팀에서 정하기 전까지 이 도메인 안에만 둔다

### 후속 작업

- 위치 공유: 끌 때 마지막 위치 즉시 삭제(`location`, 호출 방식 팀 회의), 보호자에게 `settings.updated` 이벤트(SSE·`link` 구현 후)

## Pending Decisions

- **기록 보관과 삭제**: 보관 기간·삭제 방법 미정(설정 화면의 "기록 보관과 삭제" 항목) — `history` 도메인과 공유되는 사항.
- 위치 공유 설정은 당사자 휴대폰에서만 변경 가능, 보호자 화면엔 읽기 전용으로 표시 — 이미 확정.
