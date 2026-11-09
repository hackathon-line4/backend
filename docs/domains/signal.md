# signal — 촉신호 관리

**담당:** 하은별
**범위:** 신호 목록/상세/추가/수정/삭제(M-1), 알려주기·연습(M-4), 같은 리듬 확인·추가(M-2, M-3), 리듬 프리셋·기본 신호 목록, 자주 쓰는 신호 지정(S-4, 예외로 이 도메인에 포함)

## 구현 완료 (M-2, #55)

- `GET /api/v1/signal-templates` — 검색(`q`, 이름·뜻 부분 일치)·카테고리 필터, `linkId`를 주면 그 연결 접근을 먼저 검증한 뒤 `alreadyAdded` 계산
- `POST /api/v1/links/{linkId}/signals`에 `source: TEMPLATE` 추가 — 템플릿의 이름·뜻·카테고리·아이콘·리듬을 복사, 이미 추가됐으면 409 `TEMPLATE_ALREADY_ADDED`
- `alreadyAdded`와 `TEMPLATE_ALREADY_ADDED`는 같은 기준(`existsByLinkIdAndTemplateIdAndDeletedAtIsNull`) — 삭제한 기본 신호는 둘 다 "추가 안 됨"으로 보고 재추가 가능. `DefaultSignalSeeder`(#49)가 쓰는 삭제-포함 기준(`existsByLinkIdAndTemplateId`)과는 의도적으로 다른 메서드다.

## 다른 도메인이 쓰는 계약

- **`global.signalseed.DefaultSignalSeeder`**(#49) — `void seedDefaults(UUID linkId)`. 연결 승인 시 link 도메인이 호출하면, 이 연결에 `signal_templates` 카탈로그를 전부 Signal로 복사한다(`SignalTemplateSeeder`가 구현). `LinkAccessGuard`와 반대 방향(signal이 구현, link가 의존)으로 같은 패턴을 쓴다.
  - 이미 넣은 템플릿(삭제된 것 포함)은 다시 넣지 않아 여러 번 호출해도 안전하다.
  - 리듬이 이 연결의 기존 신호와 겹치면 `RHYTHM_DUPLICATE`를 던지고 `@Transactional`로 그 호출에서 만든 것 전부 롤백한다 — 일부만 만들어진 상태가 남지 않는다.
  - `signal_templates` 시드 데이터(실제 기본 신호 구성·개수)는 아직 비어 있다 — 채워지면 추가 코드 변경 없이 그대로 동작한다.
  - link 쪽 연결(승인 로직에서 실제 호출)과 트랜잭션 경계(승인과 묶을지 베스트 에포트로 할지)는 link 담당자와 별도로 정한다.

## Pending Decisions

- **기본 신호 템플릿 구성·개수**: `signal_templates` 실제 시드 데이터(이름·카테고리·아이콘·리듬) 미확정. — 기획 확인 필요.
- **리듬 프리셋 시드 데이터**: `rhythm_presets` 실제 시드 데이터(리듬 구성) 미확정 — 기획 확인 필요. 현재는 테이블/API(GET /rhythm-presets, #57)만 구현, 실제 데이터는 넣지 않음.
- **PATCH /signals/{signalId}에서 presetId 변경 허용 여부**: PRESET_IN_USE 에러 카탈로그 페이지는 이 에러가 PATCH에도 적용된다고 적혀 있으나, PATCH 자체의 명세 페이지(Request Body, 에러 목록)에는 presetId도 PRESET_IN_USE도 없음 — 두 Notion 문서가 서로 어긋남. 확정 전까지는 PATCH에 presetId 필드를 추가하지 않고 기존 명세(이름·설명·카테고리·아이콘·리듬만 수정)대로 유지. POST /signals의 presetId 중복 사용 검증(usedBy와 동일 기준, `SignalRepository.findByLinkIdAndRhythm`)은 #57에서 구현 완료. — eunsolee 확인 필요(PATCH에서 presetId 변경 허용 여부, 허용 시 본인이 쓰는 presetId는 자기 제외 처리).
- **연결 승인 시 즐겨찾기 기본값 자동 지정** — 명세에 "협의"로 표시된 미확정 정책으로, 확정 전에는 구현하지 않음. `PUT /api/v1/links/{linkId}/favorite-signals`(S-4, #53) 자체는 구현 완료.
- **연습 완료 시점**: 당사자 기기 ack 때 '연습함'으로 처리. — 기획 확인 필요.
- **리듬 수정 시 연습 여부**: '연습 전'으로 되돌림. — 기획 확인 필요.
- **직접 만든 신호 아이콘**: 카테고리 기본 아이콘 사용. — 디자인 확인 필요.
- **ENDED 연결에서의 신호 API 응답**: `LinkAccessGuard` 실제 구현(link 도메인, #9 리뷰에서 eunsolee 제기)이 종료된 연결을 404로 처리할지 전용 코드 `LINK_ENDED`로 구분할지 — 팀 결정 필요. 결정되면 signal 쪽은 `LinkAccessGuard` 계약만 따르므로 추가 변경 없이 반영됨.
- 리듬은 박자 종류(SHORT/MEDIUM/LONG, 2~5개)만 저장, 초 단위 값은 서버 설정값(기기 테스트 후 변경 가능) — 이미 확정.
