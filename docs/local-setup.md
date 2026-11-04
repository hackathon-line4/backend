# 로컬 개발 환경

1. `.env.example`을 `.env`로 복사한다. 로컬 DB 값(`DB_*`)은 기본값 그대로 둬도 된다 — `docker-compose.yml`과 `application-local.yml`이 같은 기본값(`sonulim`/`sonulim`)을 쓰기 때문이다.

   **주의:** `.env`는 `docker compose`만 읽는다. `./gradlew bootRun`은 `.env`를 자동으로 읽지 않고 `application-local.yml`에 적힌 기본값을 쓴다 — 그래서 로컬에서는 두 기본값이 반드시 같아야 한다. `.env`의 `DB_USERNAME`/`DB_PASSWORD`를 기본값에서 바꾸면 `bootRun` 쪽에도 같은 값을 환경변수로 내보내야 한다(`set -a; . ./.env; set +a` 등).
2. Postgres 실행: `docker compose up -d` (DB 계정을 나중에 `.env`에서 바꿨다면 `docker compose down -v`로 기존 볼륨을 지우고 다시 올려야 반영된다 — Postgres는 최초 생성 시점의 계정으로 볼륨이 초기화된다.)
3. 앱 실행: `./gradlew bootRun` (Windows: `gradlew.bat bootRun`). `JWT_SECRET`은 아직 아무 코드도 참조하지 않아 비워도 뜨지만, 인증 도메인이 구현되면 필요해진다.
4. 기본 포트는 `8080`. Swagger UI는 `/swagger-ui/index.html`.

로컬 프로파일은 `spring.jpa.hibernate.ddl-auto: update`를 쓰므로, 엔티티를 추가/수정하면 앱을 다시 띄울 때 테이블이 자동으로 맞춰진다. Flyway 전환 전까지는 이 방식을 유지한다.
