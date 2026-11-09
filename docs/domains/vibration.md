# vibration — 진동 설정

**담당:** 최용주
**범위:** 내 진동 설정(세기·길이·반복) 조회/저장 (C-1)

## 현재 구현

- 내 진동 설정 조회 `GET /api/v1/me/vibration-settings`, 저장 `PUT /api/v1/me/vibration-settings` (#11) — 보호자·당사자 공통, 역할 선택 전에도 호출 가능
  - `vibration_settings` 테이블(user_id PK, intensity, length, repeat_count). 저장한 적이 있을 때만 행이 생긴다
  - 저장 전 조회는 기본값(`NORMAL`, `NORMAL`, 2)과 `updatedAt: null`. 저장 후 `updatedAt`은 마지막 저장 시각
  - `intensity`는 `null` 허용(기기가 세기 조절을 지원하지 않음). 세기·길이는 문자열로 받아 허용 외 값은 400 `VALIDATION_FAILED`(field), 반복은 1~5

### 후속 작업

- 당사자가 바꾸면 보호자에게 `settings.updated` 이벤트 (SSE·`link` 구현 후)
- 신호 푸시에 진동 설정을 실을지, messaging이 읽어 갈 조회 메서드가 필요한지 — 팀 회의에서 확인

## Pending Decisions

- 현재 "협의할 것" 목록에 이 도메인 고유 항목 없음.
- 단계 수(현재 3단계: 약하게/보통/강하게)와 반복 범위는 기기 테스트 후 확정 — 기능명세서 "8. 진동 설정" 참고.
