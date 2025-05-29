# 🎮 SteamGame Recommend API & UI

Steam 게임을 태그 기반, 사용자 프로필, 최근 플레이 정보 등 다양한 방식으로 추천해주는 시스템입니다.

## 📁 프로젝트 구성

SteamGame_public/

├── backend/                 # Spring Boot REST API

│   └── src/main/resources/

│       └── application.properties

├── templates/                    # HTML 기반 추천 UI (기능별 페이지)

│   ├── index.html           # 홈 (메뉴)

│   ├── random.html          # 태그 기반 랜덤 추천

│   ├── input.html           # 직접 입력 태그 추천

│   ├── profile.html         # 프로필 기반 추천

│   ├── recent.html          # 최근 2주 플레이 기반 추천

│   └── similar.html         # 비슷한 태그 추천

└── docker-compose.yml       # MySQL 컨테이너 설정

## ✅ 사전 준비 사항

- JDK 17 이상
- Git
- (선택) Docker & Docker Compose
- 다음 환경변수 설정 필요:
    - `STEAM_API_KEY`: Steam Web API 키
    - `GEMINI_API_KEY`: Google Gemini API 키

## 🔑 API Key 발급

### 1. Steam Web API Key

1. Steam 계정으로 [Steam API Key 등록 페이지](https://steamcommunity.com/dev/apikey)에 접속
2. 도메인 입력란(`Domain Name`)에 `localhost` 또는 여러분 서버 도메인을 입력
3. **“Register”** 클릭 → 발급된 **API Key** 복사해서 `STEAM_API_KEY` 로 설정

### 2. Google Gemini API Key

1. Google Cloud Console 열기: https://console.cloud.google.com/
2. 새 프로젝트 생성 혹은 기존 프로젝트 선택
3. 좌측 메뉴 **“APIs & Services > Library”**
    - “Vertex AI API” (또는 “Generative AI API”) 검색 → **Enable**
4. **“APIs & Services > Credentials”**
    - **Create credentials > API key** 클릭 → 생성된 키 복사
    - `GEMINI_API_KEY` 로 설정

---

## 🚀 실행 방법

### 1. 프로젝트 클론

```bash
git clone <https://github.com/your-org/SteamGame_public.git>
cd SteamGame_public
```

### 2. MySQL 컨테이너 실행

```bash
docker-compose up -d
```

- `testdb`라는 이름의 MySQL 데이터베이스가 자동 생성됩니다.

### 3. 환경변수 설정

### macOS / Linux (bash, zsh)

```bash
export STEAM_API_KEY=YOUR_STEAM_API_KEY
export GEMINI_API_KEY=YOUR_GEMINI_API_KEY
```

### Windows (PowerShell)

```powershell
$Env:STEAM_API_KEY = "YOUR_STEAM_API_KEY"
$Env:GEMINI_API_KEY = "YOUR_GEMINI_API_KEY"
```

**Tip**:

`.env`  파일을 쓰고 싶다면, `spring-boot-dotenv` 같은 라이브러리를 추가하거나

```
spring.config.import=optional:dotenv:
```

를 `application.properties` 에 선언할 수 있습니다.

### 4. 백엔드 서버 실행

```bash
cd backend
./gradlew clean bootRun
```

- 기본 포트: [http://localhost:8080](http://localhost:8080/)
- Swagger-UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

### 5. UI 페이지 확인

브라우저로 아래 파일을 열어 추천 기능을 확인할 수 있습니다:

| 기능 설명 | 파일 경로 |
| --- | --- |
| 홈 (기능 선택) | `/index.html` |
| 태그 기반 추천 | `/random.html` |
| 직접 입력 태그 추천 | `/input.html` |
| 프로필 기반 추천 | `/profile.html` |
| 최근 2주 플레이 기반 추천 | `/recent.html` |
| 비슷한 태그 추천 | `/similar.html` |

## ⚙️ 설정 예시

`backend/src/main/resources/application.properties` 파일 예시:

```bash
steam.api.key=${STEAM_API_KEY}
spring.ai.google.api-key=${GEMINI_API_KEY}
spring.datasource.url=jdbc:mysql://localhost:3306/testdb?serverTimezone=UTC
```

## 문의

이 프로젝트와 관련한 문의는 Issues 에 등록해주세요.