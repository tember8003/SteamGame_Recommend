[English](README.md) | **한국어**

# Playalike: Steam 게임 추천

태그, 설명 문장, Steam 프로필·라이브러리, 게임 임베딩을 이용해 다음에 할 Steam 게임을 찾아 주는 서비스입니다.

- 서비스: https://playalike.online (한국어 / English)
- 백엔드: Spring Boot REST API + PostgreSQL(pgvector)
- 프론트엔드: Next.js (Vercel 배포)

> 수집 대상은 리뷰 50개 이상인 게임입니다(`app.crawler.min-reviews`). 리뷰가 적은 게임은 검색되지 않을 수 있습니다.

![홈 화면](assets/readme/home.png)

---

## 주요 기능

| 기능 | 화면 | 설명 |
| --- | --- | --- |
| 태그 랜덤 추천 | `/steam/random` | 고른 태그로 랜덤 추천, 리뷰 수·한국어 지원·무료 필터 |
| 문장 입력 추천 | `/steam/input` | 원하는 게임을 문장으로 설명하면 그 문장과 가까운 게임 추천 |
| 조건 맞춤 추천 | `/steam/match` | 설명 + 플레이 시간·예산·인원 조건, 추천 이유와 주의점 제공 |
| 프로필 추천 | `/steam/profile` | Steam ID의 보유 게임으로 취향 분석 (공개 프로필만) |
| 최근 플레이 추천 | `/steam/recent` | 최근 2주 플레이 기록 기반 추천 |
| 비슷한 게임 | `/steam/similar` | 좋아하는 게임과 가까운 게임 추천 |
| 유사도 그래프 | `/steam/graph` | 비슷한 게임끼리 연결된 네트워크 탐색 (Cytoscape) |
| 라이브러리 분석 | `/library` | 플레이 기록으로 장르 거점을 만들고, 지금 해볼 게임·다시 켤 게임·안 맞는 장르를 보여 줌, 프로필 없이 결과 공유 |
| 취향 테스트 | `/taste` | 16문항에 답하면 4개 축(탐험/성장, 서사/시스템, 여유/도전, 파티/솔로) 기준 16개 유형 중 하나와 맞춤 게임 제공 |
| 게임 선택 | `/steam/choice` | 7라운드 양자택일로 취향 파악 |
| 리뷰 맞히기 | `/steam/review-guess` | 실제 Steam 리뷰를 보고 어떤 게임인지 맞히는 미니게임 |

추천 카드마다 좋아요/싫어요 피드백을 받고, 노출·피드백·퍼널 이벤트는 내부 지표 화면(`/metrics`, 토큰 필요)에서 봅니다.

| 비슷한 게임 | 유사도 그래프 |
| --- | --- |
| ![비슷한 게임](assets/readme/similar.png) | ![유사도 그래프](assets/readme/graph.png) |

| 취향 테스트 유형 |
| --- |
| ![취향 테스트](assets/readme/taste.png) |

---

## 전체 구조

```mermaid
flowchart LR
    U[사용자] --> FE["Next.js 프론트엔드<br/>(Vercel)"]
    FE -->|REST| CADDY["Caddy<br/>(HTTPS)"]
    subgraph Server["AWS Lightsail (Docker Compose)"]
        CADDY --> API["Spring Boot API"]
        API --> DB[("PostgreSQL 16<br/>+ pgvector")]
        GNN["tagemb 배치<br/>(PyTorch Geometric)"] --> DB
    end
    API --> STEAM["Steam Web API<br/>Steam Store"]
    API --> GEM["Gemini<br/>(임베딩, LLM 대체)"]
    API --> GROQ["Groq<br/>(기본 LLM)"]
```

### 데이터 준비 흐름

```mermaid
flowchart LR
    A["catalog<br/>Steam 앱 목록 동기화"] --> B["태그 동시 출현 갱신"]
    A --> C["detail<br/>상점 상세·태그·리뷰 수집"]
    C --> D["embed<br/>설명·태그를 Gemini로 임베딩<br/>(768차원)"]
    C --> E["tagemb<br/>게임-태그 그래프 GCN 학습"]
    D --> V[("games.embedding<br/>HNSW 인덱스")]
    E --> W[("태그 구조 벡터")]
```

---

## 추천 로직

### 공통 뼈대: 질의 벡터 → 최근접 후보 → 태그로 보충

문장 입력, 프로필, 최근 플레이, 비슷한 게임 추천은 질의 벡터만 다르고 같은 경로를 탑니다.

```mermaid
flowchart TD
    subgraph Q["질의 벡터 만들기"]
        Q1["문장 입력<br/>문장을 바로 임베딩"]
        Q2["프로필<br/>보유 게임 임베딩 평균"]
        Q3["최근 플레이<br/>최근 2주 게임 임베딩 평균"]
        Q4["비슷한 게임<br/>선택한 게임의 임베딩"]
    end
    Q1 & Q2 & Q3 & Q4 --> N["pgvector 최근접 40개<br/>(리뷰 수·한국어·무료 필터,<br/>보유작·이미 본 게임·싫어요 제외)"]
    N --> S["그중 4개를 무작위로 뽑음<br/>(다시 뽑을 때마다 결과가 바뀜)"]
    S --> T{"4개가 다 찼나?"}
    T -->|예| R[결과]
    T -->|아니오| F["상위 태그 + 동시 출현 태그로<br/>태그 매칭 검색해 보충"]
    F --> R
```

- 문장 입력은 LLM으로 태그도 뽑아 화면에 보여 주고, 임베딩이 실패하면 그 태그로 검색합니다.
- LLM은 Groq(`openai/gpt-oss-20b`)를 먼저 쓰고 실패하면 Gemini로 넘어갑니다. 같은 문장의 결과는 캐시합니다.

### 비슷한 게임: 두 순위기 섞기

`app.recommend.similar-interleave-enabled`를 켜면 설명문 임베딩과 GNN 태그 구조 임베딩 결과를 섞습니다.

```mermaid
flowchart LR
    S[선택한 게임] --> A["설명문 임베딩<br/>최근접"]
    S --> B["태그 구조(GNN) 임베딩<br/>최근접 30개"]
    B --> B2["유사도 순위와 인기 순위를<br/>반씩 섞어 다시 정렬, 상위 10개"]
    A --> I["Team Draft 인터리빙"]
    B2 --> I
    I --> R["결과<br/>(어느 순위기에서 왔는지 노출 기록)"]
```

GNN 쪽 조회가 실패하면 설명문 쪽 결과만 내고, 둘 다 비면 공통 경로로 내려갑니다. 노출마다 출처를 기록해 두 순위기를 비교합니다.

### 조건 맞춤 추천

```mermaid
flowchart TD
    IN["폼 조건 + 자연어 설명"] --> CR["조건 합치기<br/>LLM이 설명에서 조건 추출,<br/>폼 값이 항상 우선"]
    CR --> P{"분위기 설명이 있나?"}
    P -->|예| NP["분위기 임베딩 최근접 후보"]
    P -->|아니오 / 실패| QP["리뷰 품질 순 후보"]
    NP & QP --> F["예산·협동·인원·제외 태그 필터"]
    F --> SM["후보 뽑기<br/>플레이 시간에 맞는 후보를 먼저,<br/>좋아요 기록이 있으면 취향 가중"]
    SM --> L{"모자라나?"}
    L -->|예| RX["리뷰 하한을 낮춰 다시 채움"]
    L -->|아니오| EX
    RX --> EX["추천 이유·주의점 만들기<br/>(맞은 조건, 가장 비슷한 좋아요 게임)"]
```

### 라이브러리 분석

```mermaid
flowchart LR
    ID[Steam ID] --> OG["보유 게임·플레이 시간<br/>(Steam Web API, 10분 캐시)"]
    OG --> AN["태그별 플레이 시간 비율<br/>→ 성향 지표, 진단서"]
    OG --> GP["게임마다 대표 장르 하나에<br/>시간을 몰아 넣음 → 거점 배경"]
    OG --> PK["고르기"]
    PK --> P1["지금 해볼 게임<br/>안 해 본 게임 중 많이 한 태그와 겹치고 평가 좋은 게임"]
    PK --> P2["다시 켤 게임<br/>오래 했지만 한동안 안 켠 게임"]
    PK --> P3["안 맞는 장르<br/>잠깐 하고 그만둔 게임이 몰린 장르"]
    AN & GP & PK --> SH["결과 공유<br/>(프로필 정보 없이 90일 보관)"]
```

분석 계산은 DB·시간·난수 없이 순수 함수로 짜여 있어 테스트로 판정 기준을 고정해 두었습니다.

---

## 프로젝트 구조

```
SteamGame_Recommend/
├── backend/                     # Spring Boot API (Java 17)
│   ├── src/main/java/SteamGame/recommend/
│   │   ├── controller/          # REST 컨트롤러
│   │   ├── domain/              # recommendation, taste, library, graph, choice, guess, feedback, event, metrics ...
│   │   └── infra/               # crawler(Steam 수집), embedding, llm(Groq/Gemini), api, web(rate limit)
│   ├── src/main/resources/
│   │   ├── db/migration/        # Flyway 마이그레이션 (V1~V9)
│   │   ├── taste/               # 취향 테스트 질문·유형 데이터
│   │   ├── library/             # 라이브러리 분석 문구 데이터
│   │   └── static/              # 예전 HTML UI (현재 화면은 frontend)
│   ├── docker-compose.yml       # 로컬 PostgreSQL(pgvector)
│   ├── docker-compose.prod.yml  # 운영: postgres + app + caddy (+ tagemb 배치)
│   └── Caddyfile, Dockerfile
├── frontend/                    # Next.js 16 + React 19, ko/en
│   ├── app/[locale]/            # steam/*, library, taste 화면
│   ├── components/, lib/
│   └── public/                  # 픽셀 아트 이미지, 폰트
├── ml/tag_embedding/            # PyTorch Geometric GCN 학습 배치
├── assets/readme/               # README 이미지
└── .github/workflows/deploy.yml # 테스트, 이미지 빌드, 배포
```

---

## 기술 스택

- **Backend**: Java 17, Spring Boot 3.4 (Web, WebFlux, Data JPA, Security, Validation, Actuator), Flyway, Resilience4j, Bucket4j + Caffeine, springdoc-openapi
- **Database**: PostgreSQL 16 + pgvector
- **Frontend**: Next.js 16, React 19, TypeScript, Cytoscape.js, Vitest
- **ML**: PyTorch Geometric (GCN)
- **AI**: Gemini Embedding, Groq / Gemini 채팅 완성
- **Infra**: Docker Compose, Caddy, AWS Lightsail, GitHub Actions, GHCR, Vercel

---

## 문의

문제는 [Issues](https://github.com/tember8003/SteamGame_Recommend/issues)에 남겨 주세요.
