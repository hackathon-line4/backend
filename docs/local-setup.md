# 로컬 개발 환경

1. `.env.example`을 `.env`로 복사하고 값을 채운다(최소한 `JWT_SECRET`은 임의 값으로 채워야 앱이 뜬다).
2. Postgres 실행: `docker compose up -d`
3. 앱 실행: `./gradlew bootRun` (Windows: `gradlew.bat bootRun`)
4. 기본 포트는 `8080`. Swagger UI는 `/swagger-ui/index.html`.

로컬 프로파일은 `spring.jpa.hibernate.ddl-auto: update`를 쓰므로, 엔티티를 추가/수정하면 앱을 다시 띄울 때 테이블이 자동으로 맞춰진다. Flyway 전환 전까지는 이 방식을 유지한다.
