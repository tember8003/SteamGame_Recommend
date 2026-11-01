# Taste atlas implementation plan

**Goal:** 승인된 16개 실제 게임 캐릭터의 픽셀 도감과 상세 카드를 한국어, 영어로 제공한다.
**Architecture:** 서버 경로 컴포넌트와 작은 클라이언트 검색/공유 컴포넌트, 백엔드 문구의 정적 스냅샷을 사용한다.
**Tech Stack:** Next.js 16, React 19, TypeScript, CSS Modules, Vitest.
**Spec:** `docs/superpowers/specs/2026-11-01-taste-atlas-design.md`.

## 제약
기존 백엔드 변경, API, 라우트는 보존한다. git 상태 변경 명령을 실행하지 않는다. 새 런타임 의존성을 추가하지 않는다. 개인정보 없는 코드 기반 공유 링크를 사용한다.

## 구현 순서
1. `frontend/lib/taste/catalog.ts`, `catalog.json`을 만들고 16유형 및 필터 테스트를 먼저 실패시킨 뒤 구현한다.
2. `frontend/components/taste/TasteAtlas.tsx`, `TasteDetail.tsx`, `TastePortrait.tsx`, `taste.module.css`를 작성한다. 검색, 필터, 결과 없음, 원복, 상세, 공유를 검증한다.
3. `frontend/app/[locale]/taste/page.tsx`, `[code]/page.tsx`에 경로와 메타데이터를 추가한다. 16유형 링크는 공개 접근 가능하며 설문 완료를 요구하지 않는다.
4. `frontend/public/images/taste/{CODE}.png`에 16개 개별 픽셀아트를 넣고 제작 프롬프트와 권리 검토 상태를 문서화한다.
5. 랜딩에 도감 보조 링크만 추가한다. 기존 7라운드 테스트 진입은 유지한다.
6. 관련 테스트, 전체 테스트, lint, build, 브라우저 검색/필터/공유/반응형을 확인한다.

## 검토 집중 항목
빈 검색 결과 복구, 언어 전환 때 경로 보존, 잘못된 코드의 404, 비지원 클립보드 복구, 이미지 로딩 실패 시 이름 유지, 모든 이미지 파일과 코드의 일치.
