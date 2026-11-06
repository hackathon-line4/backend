# 개발 컨벤션

브랜치/커밋 규칙은 [CONTRIBUTING.md](../CONTRIBUTING.md)를 참고한다. 이 문서는 Java/Spring 코드 작성 규칙과 패키지 구조 규칙을 다룬다.

## Java / Spring 규칙

- **생성자 주입**만 사용한다. `@Autowired` Field Injection은 금지한다.
- Entity를 API Response로 직접 노출하지 않는다. Request/Response DTO를 분리한다.
- Controller는 요청·응답 변환만 담당한다. 비즈니스 로직을 Controller에 두지 않는다.
- Service(Application) 계층은 유스케이스 조정을 담당한다. 도메인 규칙 자체는 도메인 객체 또는 정책 객체에 둔다.
- Repository를 Controller에서 직접 호출하지 않는다. 항상 Service를 거친다.
- public setter를 무분별하게 만들지 않는다. 상태 변경은 의미 있는 이름의 메서드로 노출한다.
- `@Transactional`은 실제로 transaction boundary가 필요한 use case에만 적용한다.
- 조회 메서드에 `@Transactional(readOnly = true)`를 기계적으로 붙이지 않는다.
- enum은 DB에 문자열로 저장한다(`@Enumerated(EnumType.STRING)`). ordinal 저장 금지.
- 시간은 UTC로 저장하고 응답은 `+09:00`로 변환한다(`공통 규칙` 문서 기준). 초 단위까지 유지한다. `BaseEntity`의 `LocalDateTime` 필드는 `global.JacksonConfig`가 응답 직렬화 시 자동으로 `+09:00`·초 단위로 바꿔준다 — 도메인 코드에서 따로 변환하지 않는다. JVM 기본 시간대는 `SonulimApplication`에서 UTC로 고정되어 있으므로 `LocalDateTime.now()`는 실행 환경과 관계없이 UTC다 — `ZoneId`를 따로 지정하거나 KST로 바꿔 저장하지 않는다(로컬 로그 시각도 UTC로 찍힌다).
- 예외를 무분별하게 `RuntimeException`으로 던지지 않는다. 비즈니스 예외는 `com.sonulim.global.exception.SonulimException`에 `ErrorCode`를 명시해서 던진다 — `GlobalExceptionHandler`가 공통 규칙 문서의 응답 형식(`ApiResponse`)으로 변환한다. `ErrorCode`는 Notion 에러 코드 목록(33개) 그대로다. 새 에러가 필요하면 Notion에도 먼저 추가하고 `ErrorCode`에 반영한다.
- 한 PR에 관련 없는 변경을 섞지 않는다.

## 패키지 규칙

최상위 업무 도메인 패키지(`account, link, messaging, recipient, signal, location, history, vibration, settings`)는 전부 `com.sonulim.domain` 아래에 둔다.

각 도메인이 실제로 구현될 때, 필요한 범위에서 아래 하위 구조를 사용한다.

```
com.sonulim.domain.{domain}
├── api            # Controller, Request/Response DTO
├── application     # Service, 유스케이스 조정
├── domain          # Entity, 도메인 정책, Repository 인터페이스
└── infrastructure  # Repository 구현체, 외부 연동
```

- 현재 비어 있는 도메인 패키지에 하위 구조나 빈 클래스를 미리 일괄 생성하지 않는다. 실제 기능을 구현하는 시점에 필요한 만큼만 만든다.
- SSE/푸시처럼 여러 도메인이 공유하는 전달 메커니즘은 `global`에 둔다. 각 도메인은 자신의 이벤트를 발행만 한다.

## 예외 9개 — 장 기준과 실제 소속이 다른 엔드포인트

도메인 패키지는 기능명세서 9개 장 번호/이름을 그대로 쓰지만, 장 번호만으로 패키지를 나누면 실제 작업 담당자가 패키지 하나에 섞이는 9개 엔드포인트가 있다. 이 표가 정본이다 — 왜 이 엔드포인트가 이 패키지에 있는지는 장 번호가 아니라 이 표로 확인한다.

| 장 기준 자리 | 엔드포인트 | 기능ID | 실제 태그 | 실제 위치 패키지 |
| --- | --- | --- | --- | --- |
| settings (9장) | 연결확인 결과조회 | T-2 | `[연결]` | `link` |
| settings (9장) | 연결확인 응답(ack) | T-2 | `[연결]` | `link` |
| settings (9장) | 연결확인 요청 | T-2 | `[연결]` | `link` |
| messaging (3장) | 자주 쓰는 신호 지정 | S-4 | `[신호]` | `signal` |
| messaging (3장) | 홈 요약 | S-4 | `[홈]` | `link` |
| history (7장) | 도움 요청 상세 | H-3 | `[당사자]` | `recipient` |
| history (7장) | 신호 기록 상세 | H-2,S-3 | `[전송]` | `messaging` |
| history (7장) | 다시 보내기 | H-1,H-2,S-3 | `[전송]` | `messaging` |
| (해당 장 없음) | 이벤트 스트림(SSE) | — | `[실시간]` | `global` |

## 커밋되지 않아야 하는 것

- 사용하지 않는 추상화, 빈 Repository/Service/Controller.
- 미래에 쓸 것으로 예상되는 설정 클래스(아직 사용처가 없는 것) — 예: 아직 아무도 쓰지 않는 보안/CORS 설정.
- 소비자가 없는 인터페이스/계약을 "나중에 쓸 것 같아서" 미리 만들어두는 것.
- `.env`, 실제 비밀번호 등 민감정보.
