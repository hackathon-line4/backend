# Contributing

손울림 백엔드 저장소에 기여하는 방법을 정리한다. 코드 스타일 세부 규칙은 [docs/conventions.md](docs/conventions.md)를 참고한다.

## 브랜치 전략

```
feature/fix/refactor/chore
        ↓ PR (base: dev)
       dev
        ↓ PR (base: main, 릴리즈 시점)
       main
        ↓
   production
```

- `dev`가 integration branch이자 GitHub default branch다. **모든 작업 브랜치는 `dev`에서 분기하고, PR도 `dev`를 base로 연다.**
- `main`은 release/production branch다. `dev`가 배포 가능한 상태가 되면 `dev` → `main` PR로 승격한다. 평소 작업에서는 `main`을 직접 건드리지 않는다.

## 브랜치 이름 규칙

브랜치명은 이슈 번호와 짧은 설명을 포함한다.

```
feat/{issue-number}-{short-description}
fix/{issue-number}-{short-description}
refactor/{issue-number}-{short-description}
chore/{issue-number}-{short-description}
docs/{issue-number}-{short-description}
```

예: `feat/12-signal-crud`, `fix/27-link-code-expiry`

## 커밋 규칙

[Conventional Commits](https://www.conventionalcommits.org/)를 따른다.

| 타입 | 용도 |
|---|---|
| `feat:` | 새 기능 |
| `fix:` | 버그 수정 |
| `refactor:` | 동작 변경 없는 구조 개선 |
| `test:` | 테스트 추가/수정 |
| `docs:` | 문서 변경 |
| `chore:` | 빌드/설정 등 잡무성 변경 |
| `build:` | 빌드 시스템, 의존성 변경 |
| `ci:` | CI 설정 변경 |

- 한 커밋/PR에는 관련 없는 변경을 섞지 않는다.
- 커밋 메시지 제목은 무엇을(what)이 아니라 왜(why) 바꿨는지가 드러나도록 간결하게 쓴다.

## 작업 흐름

1. 작업 전 GitHub Issue를 생성하거나 기존 이슈를 확인한다. 이슈 제목은 `[FEAT]`, `[BUG]`, `[REFACTOR]`, `[CHORE]` 접두사를 사용한다.
2. 이슈 번호로 브랜치를 만들어 작업한다.
3. 로컬에서 `gradlew.bat clean build` (Windows) 또는 `./gradlew clean build` (macOS/Linux)로 빌드와 테스트를 통과시킨다.
4. PR을 생성하고 [PR 템플릿](.github/PULL_REQUEST_TEMPLATE.md)의 체크리스트를 채운다. 관련 이슈를 반드시 연결한다.

## 코드 스타일 요약

- 생성자 주입만 사용한다. Field Injection 금지.
- Entity를 API 응답으로 직접 노출하지 않는다. Request/Response DTO를 분리한다.
- Controller는 요청·응답 변환만 담당하고, 유스케이스 조정은 Service/Application 계층에 둔다.
- Repository는 Controller에서 직접 호출하지 않는다.
- 자세한 규칙은 [docs/conventions.md](docs/conventions.md) 참고.

## 하지 말아야 할 것

- `.env`, 실제 비밀번호 등 민감정보를 커밋하지 않는다.
- 아직 쓰이지 않는 설정이나 빈 추상화를 미리 만들지 않는다([docs/conventions.md](docs/conventions.md#커밋되지-않아야-하는-것) 참고).
- 다른 도메인 패키지의 담당자 경계를 임의로 넘지 않는다 — 걸치는 작업이면 PR에서 관련 담당자를 리뷰어로 지정한다.
