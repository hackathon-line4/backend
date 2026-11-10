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
- 토큰 갱신 `POST /api/v1/auth/refresh`, 로그아웃 `POST /api/v1/auth/logout`, 내 정보 `GET /api/v1/me`, 역할 선택 `PUT /api/v1/me/role` (#5)
- 토큰 발급·회전은 `TokenService` 한 곳에서 한다
  - 회전 방식: 쓴 갱신 토큰은 `revoked_at`을 찍고 새 토큰 ID를 `replaced_by_id`에 남긴다
  - 무효화된 갱신 토큰(이미 쓴 토큰·로그아웃한 토큰)이 다시 오면 그 사용자의 갱신 토큰을 모두 무효화하고 401 `INVALID_REFRESH_TOKEN`. 만료된 토큰은 그 토큰만 거부
  - 같은 토큰 동시 갱신은 비관적 락으로 하나만 성공 — 나머지는 재사용으로 처리되므로 앱은 갱신을 한 번만 보내야 한다(노션 명세의 단일 비행 처리)
- 로그아웃은 인증 필요. 내 토큰이 아니거나 이미 무효화된 토큰이어도 204(앱은 응답과 관계없이 로그아웃)
- 역할 선택 응답은 `{user, accessToken, refreshToken}` — 액세스 토큰에 역할이 담기므로 역할이 담긴 새 토큰을 바로 내려준다(팀 결정, 노션 명세 응답 갱신 대상). 같은 역할 재요청도 새 토큰을 발급하며 이전 갱신 토큰은 만료 전까지 유효
- `GET /me`의 `links`는 빈 배열, `pendingLinkRequest`는 `null` — `link` 도메인 구현 후 채운다
- Swagger: 인증이 필요한 API는 `@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)`를 붙이면 Authorize 버튼으로 토큰을 넣어 호출할 수 있다
- 기기·푸시 토큰 등록 `PUT /api/v1/me/devices/{installationId}` (#8) — `devices` 테이블, `installation_id`가 기본 키
  - upsert: 같은 `pushToken`을 가진 다른 기기 행 삭제, 이 사용자의 다른 기기 삭제(사용자당 1대, 임시값), 같은 설치에서 다른 계정으로 로그인하면 소유자 변경
  - 정리용 삭제는 벌크 쿼리로 바로 실행 — Hibernate는 flush 때 insert를 delete보다 먼저 보내 `push_token` unique 제약에 걸리기 때문
  - 로그아웃하면 요청한 `installationId`의 기기 삭제(내 기기일 때만)
  - 당사자가 `GET /me`를 부르면 기기의 `last_seen_at` 갱신. 요청에 기기 정보가 없어 사용자의 기기 전체(MVP는 1대)를 갱신

### 후속 작업 (해당 기능 구현 시)

- 다른 도메인용 푸시 대상 조회·무효 토큰 삭제 메서드, FCM 발송 코드(`PushSender`) 위치·담당 — 팀 회의에서 정한 뒤 추가
- 로그아웃: 이 사용자의 SSE 연결 종료 (실시간 구현 시)
- 역할 선택: 보호자 알림 설정 기본값, 당사자 위치 공유 기본값 생성 (settings 구현 시)

## Pending Decisions

- **비밀번호 찾기**: 이메일 재설정 링크 방식(부가 기능). — 기획 확인 필요.
- **기기 수**: 사용자당 활성 기기 1대(MVP). — 프론트 확인 필요. 현재 이 값으로 구현(`DeviceService.register`) — 바뀌면 이 사용자의 다른 기기를 지우는 줄만 고치면 된다.
- 비밀번호 8자 이상 + 영문/숫자 포함, JWT 액세스 1시간 / 갱신 30일(회전 방식)은 `공통 규칙` 문서에 확정되어 있음 — 별도 협의 불필요.
- 로그인 실패 제한(10분 안 5회 → 10분 잠금, 서버 메모리 저장)은 팀 확정 — 별도 협의 불필요.
