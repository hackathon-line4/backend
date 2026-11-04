# 손울림 백엔드 (sonulim-be)

보호자와 시청각장애 당사자가 진동 리듬(촉신호)으로 짧은 뜻을 주고받는 앱 "손울림"의 백엔드.

## 기술 스택

Java 17, Spring Boot 3.3.4, Gradle, Spring Data JPA + PostgreSQL(`ddl-auto: update`, Flyway는 추후 전환), JWT(`jjwt`), springdoc-openapi(Swagger), Firebase Admin SDK(FCM).

## 로컬 실행

자세한 절차는 [docs/local-setup.md](docs/local-setup.md) 참고. 요약:

```bash
cp .env.example .env   # 값 채우기
docker compose up -d   # 로컬 Postgres
./gradlew bootRun
```

## 문서

- [CLAUDE.md](CLAUDE.md) — 문서 라우터
- [docs/architecture.md](docs/architecture.md) — 패키지 구조
- [docs/conventions.md](docs/conventions.md) — 코드 컨벤션, 장-담당자 예외표
- [docs/workflow.md](docs/workflow.md) — 브랜치 전략
- [docs/domains/](docs/domains/) — 도메인별 설계 상태와 Pending Decisions
- [CONTRIBUTING.md](CONTRIBUTING.md) — 기여 가이드

## 도메인 · 담당자

| 도메인 | 담당 |
| --- | --- |
| account | 최용주 |
| link | 최은솔 |
| messaging | 조한결 |
| recipient | 조한결 |
| signal | 하은별 |
| location | 조한결 |
| history | 최은솔 |
| vibration | 최용주 |
| settings | 최용주 |
