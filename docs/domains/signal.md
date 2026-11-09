그러# signal — 촉신호 관리

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
  - `signal_templates` 시드 데이터는 `SignalTemplateCatalogSeeder`가 앱 시작 시 채운다(#61) — 기본 신호 6종.
  - link 쪽 연결(승인 로직에서 실제 호출)과 트랜잭션 경계(승인과 묶을지 베스트 에포트로 할지)는 link 담당자와 별도로 정한다.

## 구현 완료 (촉신호 → 진동 변환 규칙 변경, #61)

프론트/기획에서 기본 신호의 쉼 길이가 150/500/50ms처럼 불규칙해서 `rhythm`(SHORT/MEDIUM/LONG)만으로는
표현이 안 된다는 요청(촉신호 → 진동 변환 규칙 문서)에 따라 반영했다.

- `RhythmBeat` 상수 변경: SHORT=150, MEDIUM=700, LONG=1000ms, 박자 사이 쉼=150ms(이전 200/500/900, 쉼 300에서 변경).
- `Signal`·`SignalTemplate`에 `pulses`(`[울림, 쉼, 울림, …]` ms 배열) 컬럼 추가. `rhythm`은 TEMPLATE에 한해 null 허용 — 쉼이 불규칙해 rhythm 토큰으로 못 담는 기본 신호가 있기 때문. CUSTOM은 rhythm이 여전히 필수고 pulses는 `RhythmBeat.toPulses(rhythm)`으로 매번 계산해 같이 저장한다.
- `durationMs`는 이제 pulses 배열의 합이다(예: SHORT,SHORT,LONG → 1600ms, 이전 1900ms에서 변경).
- `RHYTHM_DUPLICATE`·`usedBy`(GET /rhythm-presets)·`PRESET_IN_USE`·같은 리듬 확인(rhythm-check) 전부 `SignalRepository.findByLinkIdAndPulses`(구 `findByLinkIdAndRhythm`)로 pulses 기준 비교하도록 통일 — CUSTOM(rhythm에서 계산)과 TEMPLATE(rhythm이 null일 수 있음)을 같은 기준으로 비교하기 위함.
- 기본 신호 6종(`SignalTemplateCatalogSeeder`, 앱 시작 시 테이블이 비어 있으면 1회 채움) 시드 완료: 나 여기 있어·밥 먹자(DAILY), 가자·기다려·누가 왔어(OUTING), 위험해(EMERGENCY). 전부 rhythm=null, pulses는 문서 값 그대로.

## Pending Decisions

- **기본 신호 템플릿 description·icon**: 이름·카테고리·pulses는 기획 확인되어 시드 완료(#61)했지만, 뜻 설명과 아이콘 값은 아직 안 받아서 null로 넣어둠. — 기획/디자인 확인 필요.
- **리듬 프리셋 시드 데이터**: `rhythm_presets` 실제 시드 데이터(리듬 구성) 미확정 — 기획 확인 필요. 현재는 테이블/API(GET /rhythm-presets, #57)만 구현, 실제 데이터는 넣지 않음.
- **PATCH /signals/{signalId}에서 presetId 변경 허용 여부**: PRESET_IN_USE 에러 카탈로그 페이지는 이 에러가 PATCH에도 적용된다고 적혀 있으나, PATCH 자체의 명세 페이지(Request Body, 에러 목록)에는 presetId도 PRESET_IN_USE도 없음 — 두 Notion 문서가 서로 어긋남. 확정 전까지는 PATCH에 presetId 필드를 추가하지 않고 기존 명세(이름·설명·카테고리·아이콘·리듬만 수정)대로 유지. POST /signals의 presetId 중복 사용 검증(usedBy와 동일 기준, `SignalRepository.findByLinkIdAndPulses`)은 #57에서 구현 완료. — eunsolee 확인 필요(PATCH에서 presetId 변경 허용 여부, 허용 시 본인이 쓰는 presetId는 자기 제외 처리).
- **연결 승인 시 즐겨찾기 기본값 자동 지정** — 명세에 "협의"로 표시된 미확정 정책으로, 확정 전에는 구현하지 않음. `PUT /api/v1/links/{linkId}/favorite-signals`(S-4, #53) 자체는 구현 완료.
- **연습 완료 시점**: 당사자 기기 ack 때 '연습함'으로 처리. — 기획 확인 필요.
- **리듬 수정 시 연습 여부**: '연습 전'으로 되돌림. — 기획 확인 필요.
- **직접 만든 신호 아이콘**: 카테고리 기본 아이콘 사용. — 디자인 확인 필요.
- **ENDED 연결에서의 신호 API 응답**: `LinkAccessGuard` 실제 구현(link 도메인, #9 리뷰에서 eunsolee 제기)이 종료된 연결을 404로 처리할지 전용 코드 `LINK_ENDED`로 구분할지 — 팀 결정 필요. 결정되면 signal 쪽은 `LinkAccessGuard` 계약만 따르므로 추가 변경 없이 반영됨.
- 리듬은 박자 종류(SHORT/MEDIUM/LONG, 2~5개)만 저장, 초 단위 값(150/700/1000ms, 쉼 150ms)은 서버 설정값(기기 테스트 후 변경 가능) — 이미 확정(#61에서 값 갱신).
