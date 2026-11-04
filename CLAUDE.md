# 손울림 백엔드 — Claude Code Guide

이 문서는 라우터 역할만 한다. 상세 내용은 각 문서를 참고한다.

- 아키텍처/패키지 구조 → docs/architecture.md
- 코드 컨벤션, 장-담당자 예외표 → docs/conventions.md
- 브랜치/작업 흐름 → docs/workflow.md
- 도메인별 설계/현재 상태/Pending Decisions → docs/domains/*.md
- API 계약 → Swagger(/swagger-ui, /v3/api-docs) — 여기엔 API 목록을 적지 않는다
- 로컬 개발 환경 → docs/local-setup.md
- 브랜치/커밋/PR 규칙 → CONTRIBUTING.md

## 항상 지킬 원칙

- 기능명세서/기획에서 확정되지 않은 비즈니스 규칙(타임아웃, threshold 등)을 임의로 만들지 않는다. 미확정이면 해당 도메인 문서(docs/domains/*.md)의 Pending Decisions로 남긴다.
- 관련 없는 도메인을 리팩터링하지 않는다.
- 작업 완료 전 항상 clean build(`./gradlew clean build`).
- `dev`가 integration branch, `main`은 release/production branch.
- 아직 쓰이지 않는 설정(보안, CORS 등)이나 빈 추상화를 미리 만들지 않는다 — 실제 구현 시점에 필요한 만큼만.

## 외부 지식 소스

- 기능/비즈니스 요구사항, 팀 결정사항 → Notion(기능명세서, API 명세서, 회의록)
- 기술 아키텍처/코딩 컨벤션 → 이 레포 docs/

여러 source가 충돌하면 임의로 해석하지 않고 충돌 내용을 먼저 사용자에게 보고한다.
