# Architecture

## 현재 형태: 모듈러 모놀리스

손울림 백엔드는 단일 Spring Boot 애플리케이션으로 배포되는 모듈러 모놀리스다. 도메인 간 경계는 패키지 단위로만 구분된다. 별도 서비스 분리는 필요성이 확인되기 전까지 계획하지 않는다.

## 패키지 최상위 구조

```
com.sonulim
├── global          # BaseEntity, 공통 예외/ErrorCode, 보안 설정, SSE 이벤트 허브
└── domain
    └── {업무 도메인}  # 기능명세서 장(章) 단위, 아래 표 참고
```

`com.sonulim.domain.{업무}.domain`처럼 `domain`이 두 번 나오는 경우가 있다. 첫 번째 `domain`은 업무 도메인을 묶는 최상위 그룹이고, 두 번째 `domain`은 그 업무 도메인 안의 Domain Layer(Entity, 도메인 규칙, Repository 인터페이스)다.

## 도메인 책임

| 패키지 | 책임 (기능명세서 장) | 담당 |
| --- | --- | --- |
| `domain.account` | 인증 · 계정 — 회원가입, 로그인, 역할 선택, 로그아웃, 토큰 갱신 (1장) | 최용주 |
| `domain.link` | 계정 연결 — 연결 코드/QR, 연결 요청, 연결 해제, 연결 상태 확인, 홈 요약 (2장 + 예외) | 최은솔 |
| `domain.messaging` | 보호자→당사자 신호 전송 — 보내기, ack, 재생 보고, 터치 응답, 다시 보내기, 더 기다리기 (3장 + 예외) | 조한결 |
| `domain.recipient` | 당사자 기능 — 보호자 부르기, 도움 요청 (4장 + 예외) | 조한결 |
| `domain.signal` | 촉신호 관리 — 신호 CRUD, 연습, 즐겨찾기 (5장 + 예외) | 하은별 |
| `domain.location` | 위치 확인 (6장) | 조한결 |
| `domain.history` | 신호 기록 (7장, 예외로 일부 축소) | 최은솔 |
| `domain.vibration` | 진동 설정 (8장) | 최용주 |
| `domain.settings` | 설정 (9장, 예외로 일부 축소) | 최용주 |
| `global` | 도메인 전반에서 공유하는 베이스 엔티티, 예외 처리, 보안 설정, SSE 이벤트 허브 | — |

각 패키지의 `package-info.java`가 책임 설명의 정본이다. 이 문서와 어긋나면 `package-info.java`를 기준으로 갱신한다.

**패키지를 장(章) 번호가 아니라 실제 API 태그/담당자 기준으로 조정한 9개 예외가 있다 — [docs/conventions.md](conventions.md#예외-9개-장-기준과-실제-소속이-다른-엔드포인트)에서 표로 관리한다.**

## 계층 구조: API → Application → Domain, Infrastructure는 Domain을 구현

도메인이 실제로 구현될 때는 [docs/conventions.md](conventions.md#패키지-규칙)의 `api / application / domain / infrastructure` 구조를 그 도메인 범위에서 사용한다.

- `api`: Controller, Request/Response DTO. `application`만 호출한다.
- `application`: 유스케이스 조정(Service). `domain`을 사용해 흐름을 조립하고, 트랜잭션 경계를 가진다.
- `domain`: Entity, 도메인 정책/규칙, Repository 인터페이스. 다른 계층에 의존하지 않는다.
- `infrastructure`: `domain`이 정의한 Repository 인터페이스의 구현체, 외부 시스템 연동(FCM 등).

의존 방향은 `api → application → domain ← infrastructure`다.

## 도메인 간 의존 방향

- 원칙적으로 도메인 패키지는 서로 직접 의존하지 않는다. 연동이 필요하면(예: `link`의 홈 요약이 `signal`/`location`/`history`의 데이터를 모아 보여줌) 이벤트나 명시적인 애플리케이션 서비스 호출 등 느슨한 결합을 우선 검토한다.
- 모든 도메인은 `global`에 의존할 수 있지만, `global`은 어떤 도메인에도 의존하지 않는다.

## 실시간 전달 (SSE / 푸시)

앱이 열려 있을 때는 `GET /api/v1/events`(SSE)로 상태 변화를 밀어주고, 닫혀 있을 때는 FCM 데이터 푸시로 전달한다. 이 메커니즘 자체(SSE 연결 관리, 이벤트 발행 허브)는 특정 업무 도메인이 아니라 `global`에 둔다 — 각 도메인은 자기 이벤트를 이 허브에 발행만 한다. 푸시 종류·서버 타이머 등 세부 규칙은 각 도메인 문서([docs/domains/](domains/))의 관련 항목을 참고한다.

## 데이터 저장

PostgreSQL + Spring Data JPA. 스키마는 당분간 `spring.jpa.hibernate.ddl-auto: update`로 관리한다 — Flyway 마이그레이션 도입은 보류 상태이며, 전환 시점은 팀이 따로 정한다.

`global.BaseEntity`(`createdAt`/`updatedAt`)는 `global.JpaAuditingConfig`의 `@EnableJpaAuditing`으로 자동 기록된다. `@SpringBootApplication`이 아닌 별도 설정 클래스에 둬서 `@WebMvcTest` 같은 슬라이스 테스트에는 적용되지 않는다.
