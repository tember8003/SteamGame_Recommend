# 🎮 SteamGame Recommend API & UI

Steam 게임의 태그 정보를 기반으로 사용자 프로필, 최근 플레이 정보, Gemini API 등을 활용하여 게임을 추천해주는 시스템입니다.

> 🔍 약 96,000개의 게임 데이터 기반 (2025년 6월 기준)
>

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
│   │           ├── recent.html       # 최근 2주 플레이 기반 추천
│   │           └── similar.html      # 비슷한 태그 추천
├── electron/                         # Electron 데스크톱 앱
│   ├── main.js                       # 메인 프로세스
│   ├── package.json                  # 빌드 설정
│   ├── html/
│   │   ├── config.html               # API 키 설정
│   │   └── loading.html              # 로딩 화면
│   └── resources/
│       └── recommend-0.0.1-SNAPSHOT.jar
└── docker-compose.yml                # MySQL 컨테이너 설정

```

---

## ✅ 사전 준비 사항

- **공통**
  - JDK 17 이상, Git, Docker & Docker Compose
  - MySQL: 포트 3307 / Redis: 포트 6379 (포트 충돌 주의)
- **데스크톱 앱**
  - Windows 10/11 (64-bit)
  - Java 17 이상 설치
  - 앱 실행 시 API 키 설정 가능


---

## 🔑 API Key 발급

### 1. Steam Web API Key

1. [Steam API Key 등록 페이지](https://steamcommunity.com/dev/apikey) 접속 후 Steam 계정 로그인
2. Domain Name에 임의 주소 입력 (예: `localhost`)
3. "I agree" 체크 후 "Register" 클릭
4. 발급된 API Key 복사 및 보관

> Steam Guard 미인증 또는 커뮤니티 제한 계정은 발급 불가
>

### 2. Google Gemini API Key

1. [Google AI Studio](https://aistudio.google.com/app/apikey) 접속 및 로그인
2. "Create API Key" 클릭
3. 생성된 API Key 복사 및 보관

---

## 🚀 실행 방법

### 방법 1: 데스크톱 앱 사용 (권장)

1. **다운로드 & 설치**
  - [Releases 페이지](https://github.com/tember8003/SteamGame_Recommend/releases)에서 최신 버전 다운로드
  - `Steam-Game-Recommend-Setup-x.x.x.exe` 실행
2. **앱 실행**
  - 첫 실행 시 API 키 설정 화면 자동 표시
  - Steam API Key 및 Gemini API Key 입력
  - `Skip for Now` 클릭 시 API 키 없이 태그 검색 가능
3. **기능 이용**
  - 모든 기능이 하나의 앱으로 실행
  - 별도 서버 환경 변수 설정 불필요


---

### 방법 2: 개발자/서버 실행

1. **프로젝트 클론**

    ```bash
    git clone https://github.com/tember8003/SteamGame_Recommend.git
    cd SteamGame_Recommend
    ```

2. **MySQL 컨테이너 실행**

    ```bash
    docker-compose up -d
    ```

  - `testdb` 데이터베이스 자동 생성

3. **환경 변수 설정**
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

4. **서버 실행**

    ```bash
    ./gradlew clean bootRun
    ```

  - 기본 서버: [http://localhost:8080](http://localhost:8080/)
  - Swagger-UI: http://localhost:8080/swagger-ui/index.html
  - OpenAPI JSON: http://localhost:8080/v3/api-docs


---

## 🖥️ UI 페이지

| 기능 설명 | 경로 |
| --- | --- |
| 홈 (기능 선택) | `/` |
| 태그 기반 추천 | `/random.html` |
| 직접 입력 태그 추천 | `/input.html` |
| 프로필 기반 추천 | `/profile.html` |
| 최근 2주 플레이 추천 | `/recent.html` |
| 비슷한 태그 추천 | `/similar.html` |

---

## 📦 Electron 앱 빌드 (개발자용)

1. **준비**

    ```bash
    cd electron
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

3. **앱 빌드**

    ```bash
    cd electron
    npm run build-win  # Windows 빌드
    ```

  - 빌드 결과: `electron/dist/`


---

## ⚙️ 설정 예시

```
steam.api.key=${STEAM_API_KEY}
spring.ai.google.api-key=${GEMINI_API_KEY}
spring.datasource.url=jdbc:mysql://localhost:3307/testdb?serverTimezone=UTC
```

---

## 🎯 주요 기능

- **🔍 태그 기반 추천**
  - 랜덤 추천: 태그 기반 랜덤 게임 추천
  - 비슷한 태그: 특정 게임과 유사한 태그 게임 추천
- **👤 프로필 기반 추천**
  - Steam ID 라이브러리 분석
- **⏰ 최근 플레이 기반 추천**
  - 최근 2주 플레이 게임 분석
  - 현재 관심사 기반 추천

---

## 🆘 문제 해결

### 데스크톱 앱

- **Java 오류**: Java 17 이상 설치 확인
- **API 키 오류**: 올바른 API 키 입력 확인
- **포트 충돌**: 8080 포트 사용 여부 확인

### 서버 실행

- **MySQL 연결 오류**: Docker 컨테이너 실행 확인
- **환경 변수 오류**: API 키 설정 확인

---

## 문의

프로젝트 관련 이슈는 [Issues 페이지](https://github.com/tember8003/SteamGame_Recommend/issues)에 등록해 주세요.