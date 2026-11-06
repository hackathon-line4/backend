# account — 인증 · 계정

**담당:** 최용주
**범위:** 회원가입(A-1), 로그인·자동로그인(A-2), 역할 선택(A-3), 로그아웃(A-4), 기기·푸시 토큰 등록

## 현재 구현

- 회원가입 `POST /api/v1/auth/signup`, 로그인 `POST /api/v1/auth/login` (#1)
- 이메일은 소문자로 바꿔 저장·조회(대소문자 무시). 비밀번호는 BCrypt 해시로 저장
- 응답의 `links`는 `link` 도메인 구현 전까지 빈 배열
- 액세스 토큰(JWT, HS256)과 갱신 토큰 발급. 갱신 토큰은 원문 대신 SHA-256 해시만 `refresh_tokens`에 저장
- JWT 검증 필터·인증 실패 응답(401 `UNAUTHORIZED`/`TOKEN_EXPIRED`)은 `global.security`에 있음 — 다른 도메인은 컨트롤러에서 `@AuthenticationPrincipal AuthenticatedUser`로 사용자 ID를 받는다
- 로그인 실패 제한: 같은 이메일로 10분 안에 5번 실패하면 10분 잠금(429 `TOO_MANY_ATTEMPTS`, `details.retryAfterSec`). 가입되지 않은 이메일도 똑같이 센다
  - 실패 횟수는 서버 메모리에 저장 — 재시작하면 초기화되고, 서버를 2대 이상으로 늘리면 Redis 같은 공유 저장소로 옮겨야 한다
- 로컬 `JWT_SECRET`은 비워 두면 `application-local.yml`의 개발용 키를 쓴다. 운영은 환경 변수로 32바이트 이상 키를 반드시 지정

## Pending Decisions

- **비밀번호 찾기**: 이메일 재설정 링크 방식(부가 기능). — 기획 확인 필요.
- **기기 수**: 사용자당 활성 기기 1대(MVP). — 프론트 확인 필요.
- 비밀번호 8자 이상 + 영문/숫자 포함, JWT 액세스 1시간 / 갱신 30일(회전 방식)은 `공통 규칙` 문서에 확정되어 있음 — 별도 협의 불필요.
- 로그인 실패 제한(10분 안 5회 → 10분 잠금, 서버 메모리 저장)은 팀 확정 — 별도 협의 불필요.
