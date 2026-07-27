🌐 **한국어** | [English](README.md)

# 🎮 Steam Game Recommend API & UI

태그 정보, 유저 프로필, 최근 플레이 기록을 활용한 스팀 게임 추천 시스템

> **참고**: H2 데이터베이스 용량 최적화를 위해 리뷰가 50개 이상인 게임만 포함되어 있습니다. 이로 인해 일부 게임은 검색되지 않을 수 있습니다.

---

## 📁 프로젝트 구조

```
recommend/
├── src
│   ├── main
│   │   ├── java/SteamGame.recommend/  # Spring Boot REST API
│   │   └── resources/
│   │       ├── application.properties
│   │       └── static/               # HTML 기반 UI
│   │           ├── index.html        # 홈 (기능 메뉴)
│   │           ├── random.html       # 태그 기반 랜덤 추천
│   │           ├── input.html        # 직접 입력 태그 추천
│   │           ├── profile.html      # 프로필 기반 추천
│   │           ├── recent.html       # 최근 2주 플레이 추천
│   │           ├── similar.html      # 유사 태그 추천
│   │           ├── choice.html       # 게임 선택 인터랙티브 기능
│   │           └── review-guess.html # 리뷰 맞추기 게임
├── electron/                         # Electron 데스크탑 앱
│   ├── main.js                       # 메인 프로세스
│   ├── package.json                  # 빌드 설정
│   ├── html/
│   │   ├── config.html               # API 키 설정
│   │   └── loading.html              # 로딩 화면
│   └── resources/
│       └── recommend-0.0.1-SNAPSHOT.jar
└── data/                             # H2 데이터베이스 파일 (자동 생성)
    └── gamedb.mv.db                  # H2 데이터베이스 파일
```

---

## 📑 사전 요구사항

- **공통 요구사항**
    - JDK 17 이상, Git
    - 별도 외부 데이터베이스 불필요 (H2 내장)
- **데스크탑 애플리케이션**
    - Windows 10/11 (64-bit)
    - Java 17 이상 설치 필요
    - 앱 실행 시 API 키 설정 가능

---

## 🔑 API 키 설정

### 1. Steam Web API 키

1. [Steam API 키 등록 페이지](https://steamcommunity.com/dev/apikey)에 방문하여 Steam 계정으로 로그인
2. 도메인 이름 입력 (예: `localhost`)
3. "동의함"에 체크하고 "등록" 클릭
4. 생성된 API 키 복사 및 저장

> 참고: Steam Guard 인증이 필요합니다. 커뮤니티 제한 계정은 API 키를 발급받을 수 없습니다.

### 2. Google Gemini API 키

1. [Google AI Studio](https://aistudio.google.com/app/apikey)에 방문하여 로그인
2. "API 키 만들기" 클릭
3. 생성된 API 키 복사 및 저장

---

## 🖥️ 실행 방법

### 방법 1: 데스크탑 애플리케이션 (권장)

1. **다운로드 및 설치**
    - [Releases 페이지](https://github.com/tember8003/SteamGame_Recommend/releases)에서 최신 버전 다운로드
    - `Steam-Game-Recommend-Setup-x.x.x.exe` 실행

2. **애플리케이션 실행**
    - 최초 실행 시 API 키 설정 화면이 자동으로 표시됨
    - Steam API 키와 Gemini API 키 입력
    - API 키 없이 태그 검색만 사용하려면 `지금은 건너뛰기` 클릭
    - API 키는 로컬에 저장됨: `%AppData%\steam-game-recommend\api-keys.json`

3. **기능 사용**
    - 모든 기능이 하나의 앱 안에서 실행됨
    - 별도 서버 환경 변수 설정 불필요

---

### 방법 2: 개발자/서버 실행

1. **프로젝트 클론**

    ```bash
    git clone https://github.com/tember8003/SteamGame_Recommend.git
    cd SteamGame_Recommend
    ```

2. **환경 변수 설정** (선택사항)
    - macOS/Linux (bash, zsh)

       ```bash
       export STEAM_API_KEY=YOUR_STEAM_API_KEY
       export GEMINI_API_KEY=YOUR_GEMINI_API_KEY
       ```

    - Windows (PowerShell)

       ```powershell
       $Env:STEAM_API_KEY = "YOUR_STEAM_API_KEY"
       $Env:GEMINI_API_KEY = "YOUR_GEMINI_API_KEY"
       ```

3. **서버 시작**

    ```bash
    cd backend
    ./gradlew clean bootRun
    ```

    - 기본 서버 주소: [http://localhost:8888](http://localhost:8888/)
    - H2 콘솔: http://localhost:8888/h2-console (데이터베이스 관리)
    - Swagger-UI: http://localhost:8888/docs
    - OpenAPI JSON: http://localhost:8888/v3/api-docs

---

## 🖥️ UI 페이지

| 기능 설명 | 경로 |
| --- | --- |
| 홈 (기능 선택) | `/` |
| 태그 기반 추천 | `/random.html` |
| 직접 입력 태그 추천 | `/input.html` |
| 프로필 기반 추천 | `/profile.html` |
| 최근 2주 플레이 추천 | `/recent.html` |
| 유사 태그 추천 | `/similar.html` |

---

## 📦 Electron 앱 빌드 (개발자용)

1. **설정**

    ```bash
    cd backend/electron
    npm install
    ```

2. **JAR 파일 준비**

    ```bash
    # 메인 프로젝트에서 JAR 빌드
    cd ..
    ./gradlew clean bootJar
    
    # JAR 파일 복사
    cp build/libs/recommend-0.0.1-SNAPSHOT.jar electron/resources/
    ```

3. **애플리케이션 빌드**

    ```bash
    cd electron
    npm run build-win  # Windows 빌드
    ```

    - 빌드 결과물: `electron/dist/`

---

## ⚙️ 설정 예시

```properties
# API 키
steam.api.key=${STEAM_API_KEY}
spring.ai.google.api-key=${GEMINI_API_KEY}

# H2 데이터베이스 설정
spring.datasource.url=jdbc:h2:file:./data/gamedb;DB_CLOSE_DELAY=-1;MODE=MySQL;AUTO_SERVER=TRUE;DATABASE_TO_LOWER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;CHARSET=UTF-8
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# H2 콘솔
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# 서버 설정
server.port=8888
```

---

## 📖 주요 기능

- **🔍 태그 기반 추천**
    - 랜덤 추천: 태그 기반 랜덤 게임 추천
    - 유사 태그: 특정 게임과 유사한 태그를 가진 게임 추천
- **😊 프로필 기반 추천**
    - Steam ID 라이브러리 분석
- **⏰ 최근 플레이 기반 추천**
    - 최근 2주간 플레이한 게임 분석
    - 현재 관심사 기반 추천

---

## 🆘 문제 해결

### 데스크탑 애플리케이션

- **Java 오류**: Java 17 이상이 설치되어 있는지 확인
- **API 키 오류**: 올바른 API 키가 입력되어 있는지 확인
- **포트 충돌**: 8888 포트가 사용 중인지 확인

### 서버 실행

- **포트 충돌**: 다른 애플리케이션이 8888 포트를 사용 중인지 확인
- **환경 변수 오류**: API 키가 올바르게 설정되어 있는지 확인 (기본 기능에는 선택사항)
- **데이터베이스 오류**: H2 데이터베이스 파일은 `./data/` 디렉토리에 자동으로 생성됨

---

## 📁 API 키 저장 위치

데스크탑 애플리케이션은 API 키를 로컬에 저장합니다:
- **Windows**: `%AppData%\steam-game-recommend\api-keys.json`
- **전체 경로 예시**: `C:\Users\[사용자명]\AppData\Roaming\steam-game-recommend\api-keys.json`

API 키는 본인의 기기에만 저장되며 외부로 공유되지 않습니다.

---

## 💾 데이터베이스

애플리케이션은 **H2 내장 데이터베이스**를 사용하며 다음과 같은 특징이 있습니다:
- **자동 초기화**: 최초 실행 시 데이터베이스와 테이블이 자동으로 생성됨
- **데이터 영속성**: 게임 데이터가 `./data/gamedb` 파일에 저장되어 재시작 후에도 유지됨
- **특수 문자 지원**: ®, ™ 등 유니코드 문자 처리
- **MySQL 호환 모드**: MySQL 문법과의 호환성 보장
- **H2 콘솔**: `/h2-console`에서 웹 인터페이스로 데이터베이스 관리 가능
- **외부 의존성 없음**: 별도 데이터베이스 서버 설치 불필요

---

## 🌐 API 엔드포인트

### 게임 추천
- `POST /api/recommend/random` - 태그 기반 랜덤 추천
- `POST /api/recommend/input` - 텍스트 입력 기반 추천
- `POST /api/recommend/profile` - 프로필 기반 추천
- `POST /api/recommend/recent-play` - 최근 플레이 기반 추천
- `POST /api/recommend/similar` - 유사 게임 추천

### 게임 데이터
- `GET /api/games/appid?appid={id}` - AppID로 게임 조회
- `GET /api/games/tags` - 전체 태그 목록 조회
- `GET /api/games/search?query={name}` - 게임 이름 검색

### 인터랙티브 기능
- `POST /api/choice/start` - 게임 선택 세션 시작
- `POST /api/choice/select` - 선택 제출
- `POST /api/choice/result` - 선택 결과 추천 받기

### 리뷰 맞추기 게임
- `POST /api/review-guess/start` - 리뷰 맞추기 게임 시작
- `POST /api/review-guess/answer` - 답변 제출
- `GET /api/review-guess/{sessionId}/result` - 게임 결과 조회

---

## 🛠️ 기술 스택

- **백엔드**: Spring Boot 3.x, Java 17
- **데이터베이스**: H2 (내장, 프로덕션) / MySQL (개발)
- **프론트엔드**: HTML, CSS, JavaScript
- **데스크탑**: Electron
- **AI 연동**: Google Gemini API
- **외부 API**: Steam Web API
- **빌드**: Gradle
- **데이터베이스 콘솔**: H2 Console (내장 웹 인터페이스)

---

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 제공됩니다.

---

## 📞 지원

프로젝트 관련 문제는 [Issues 페이지](https://github.com/tember8003/SteamGame_Recommend/issues)에 등록해 주세요.
