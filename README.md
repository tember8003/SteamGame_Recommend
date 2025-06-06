# 🎮 SteamGame Recommend API & UI

Steam 게임의 태그 정보를 기반으로 사용자 프로필, 최근 플레이 정보, Gemini API 등을 활용하여 게임을 추천해주는 시스템입니다.

> 🔍 약 96,000개의 게임 데이터 기반 (2025년 6월 기준). 일부 게임은 추천되지 않을 수 있습니다.
>

---

## 📁 프로젝트 구성

```
recommend/
├── src/main
│ └── java/SteamGame.recommend/ # Spring Boot REST API
│ └── resources/application.properties
├ └── resources/static/ # HTML 기반 추천 UI
│     ├── index.html # 홈 (기능 메뉴)
│     ├── random.html # 태그 기반 랜덤 추천
│     ├── input.html # 직접 입력 태그 추천
│     ├── profile.html # 프로필 기반 추천
│     ├── recent.html # 최근 2주 플레이 기반 추천
│     └── similar.html # 비슷한 태그 추천
└── docker-compose.yml # MySQL 컨테이너 설정
```

---

## ✅ 사전 준비 사항

- JDK 17 이상
- Git
- Docker & Docker Compose
- 다음 환경변수 설정 필요:
  - `STEAM_API_KEY`: Steam Web API 키
  - `GEMINI_API_KEY`: Google Gemini API 키
- 사용 포트: **3307 (MySQL)**, **6379 (Redis)** → 포트 충돌이 없는지 확인 필수

## 🔑 API Key 발급

### 1. Steam Web API Key

Steam 계정으로 로그인한 후 Steam API Key 등록 페이지(https://steamcommunity.com/dev/apikey)에 접속하세요.

Domain Name에는 아무 주소나 입력하세요. (예: localhost)

하단의 "I agree" 체크 후 "Register"를 클릭하세요.

발급된 API Key를 복사하여 하단 실행 방법 3의 환경변수 설정에서 `STEAM_API_KEY`로 설정하세요.

> ⚠️ Steam Guard 미인증 계정이나 커뮤니티 제한 계정은 발급이 불가합니다.
>

### 2. Google Gemini API Key 발급 방법 (Google AI Studio 기준)

Google AI Studio(https://aistudio.google.com/app/apikey)에 접속하세요.

로그인 후, "Create API Key"를 클릭하세요.

생성된 API Key를 복사하여 하단 실행 방법 3의 환경변수 설정에서 `GEMINI_API_KEY`로 설정하세요.

---

## 🚀 실행 방법

### 1. 프로젝트 클론

```bash
git clone &lt;https://github.com/tember8003/SteamGame_Recommend.git&gt;
cd SteamGame_Recommend
```

---

### 2. MySQL 컨테이너 실행

```bash
docker-compose up -d
```

- `testdb`라는 이름의 MySQL 데이터베이스가 자동으로 생성됩니다.

---

### 3. 환경변수 설정

### 방법 1.

- **`.env.example` 파일 복사**
  - 프로젝트 최상단(루트) 폴더의 `.env.example` 파일을 `.env` 파일로 복사합니다.
  - macOS / Linux:

      ```bash
      cp .env.example .env
      ```

  - Windows (PowerShell):

      ```powershell
      copy .env.example .env
      ```

- **`.env` 파일 설정하기**
  - 생성된 `.env` 파일을 엽니다.
  - `STEAM_API_KEY`와 `GEMINI_API_KEY`에 발급받은 API 키를 입력합니다.

      ```
      STEAM_API_KEY=YOUR_STEAM_API_KEY
      GEMINI_API_KEY=YOUR_GEMINI_API_KEY
      ```


### 방법 2.

**macOS / Linux (bash, zsh)**

```bash
export STEAM_API_KEY=YOUR_STEAM_API_KEY
export GEMINI_API_KEY=YOUR_GEMINI_API_KEY
```

**Windows (PowerShell)**

```powershell
$Env:STEAM_API_KEY = "YOUR_STEAM_API_KEY"
$Env:GEMINI_API_KEY = "YOUR_GEMINI_API_KEY"
```

---

### 4. 서버 실행

```bash
./gradlew clean bootRun
```

- 기본 포트: [http://localhost:8080](http://localhost:8080/)
- Swagger-UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## 🖥️ UI 페이지 확인

브라우저에서 아래 경로로 접속하여 추천 기능을 확인할 수 있습니다:

| 기능 설명 | 파일 경로 |
| --- | --- |
| 홈 (기능 선택) | `/` |
| 태그 기반 추천 | `/random.html` |
| 직접 입력 태그 추천 | `/input.html` |
| 프로필 기반 추천 | `/profile.html` |
| 최근 2주 플레이 기반 추천 | `/recent.html` |
| 비슷한 태그 추천 | `/similar.html` |

---

## ⚙️ 설정 예시

`/src/main/resources/application.properties` 파일 예시:

```bash
steam.api.key=${STEAM_API_KEY}
spring.ai.google.api-key=${GEMINI_API_KEY}
spring.datasource.url=jdbc:mysql://localhost:3307/testdb?serverTimezone=UTC
```

## 문의

이 프로젝트와 관련한 문의는 Issues에 등록해 주세요.