# 손울림 백엔드 (sonulim-be)

보호자와 시청각장애 당사자가 진동 리듬(촉신호)으로 짧은 뜻을 주고받는 앱 "손울림"의 백엔드.

## 기술 스택

Java 17, Spring Boot 3.3.4, Gradle, Spring Data JPA + PostgreSQL(`ddl-auto: update`, Flyway는 추후 전환), JWT(`jjwt`), springdoc-openapi(Swagger), Firebase Admin SDK(FCM).

## 실행 (프론트 연동용)

Docker Desktop만 있으면 된다. JDK·DB 설치나 `.env`는 필요 없다.

```bash
git clone https://github.com/hackathon-line4/backend.git
cd backend
git checkout dev
docker compose up -d --build   # 처음엔 이미지 빌드로 몇 분 걸린다
```

- API: `http://localhost:8080` · Swagger: http://localhost:8080/swagger-ui/index.html
- 최신 코드 반영: `git pull` 후 `docker compose up -d --build`
- 끄고 켜기: Docker Desktop의 `backend` 묶음에서 ■·▶︎ (또는 `docker compose stop` / `docker compose start`)
- DB 초기화: `docker compose down -v` 후 다시 `docker compose up -d --build`
- 푸시 알림은 기본적으로 실제로 보내지 않고 서버 로그에만 남는다. 실시간 이벤트(SSE)는 동작한다.
  - 실제 FCM 발송: 백엔드에게 받은 서비스 계정 키를 `secret/` 폴더에 두고, `.env`에 `FIREBASE_KEY_FILE=<키 파일 이름>`을 넣은 뒤 `docker compose up -d`. 키는 절대 커밋·공유 채널에 올리지 않는다.
- 에뮬레이터·실기기에서 접속: Android 에뮬레이터는 `http://10.0.2.2:8080`, 같은 와이파이의 실기기는 `http://<이 컴퓨터의 IP>:8080`

## 백엔드 개발

DB만 Docker로 띄우고 앱은 IDE·Gradle로 실행한다. 자세한 절차는 [docs/local-setup.md](docs/local-setup.md).

```bash
docker compose up -d postgres
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
