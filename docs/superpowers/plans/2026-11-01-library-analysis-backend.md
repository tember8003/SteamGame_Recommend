# Steam 라이브러리 분석 백엔드 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Steam ID로 라이브러리를 읽어 장부, 판결문, 성향 지표, 진단서를 계산하고, Steam ID 없이 공유할 수 있는 API를 기존 API를 건드리지 않고 추가한다.

**Architecture:** 입력 해석(`SteamIdResolver`)과 Steam 호출(`SteamLibraryClient`, 응답 해석은 순수 함수 `SteamLibraryParser`)을 기존 `SteamApiService`와 분리한다. 판정은 순수 함수 `LibraryAnalyzer`, 문구는 버전 붙은 JSON 리소스, 공유는 `library_share` 테이블에 저장한다.

**Tech Stack:** Java 17, Spring Boot 3.4.13, WebClient, Caffeine, JdbcTemplate/JPA, Flyway, PostgreSQL 16, JUnit 5, Testcontainers

**Spec:** `docs/superpowers/specs/2026-11-01-library-analysis-backend-design.md` (원래 저장소 폴더 `C:\Users\black\Desktop\Projects\SteamGame_Recommend` 기준, docs는 커밋하지 않음)

작업 폴더(워크트리): `C:\Users\black\Desktop\Projects\SteamGame_Recommend-library`. 경로는 그 안의 `backend/` 기준이다. `$B` = `src/main/java/SteamGame/recommend`, `$T` = `src/test/java/SteamGame/recommend`.

## Global Constraints

- 기존 API의 경로, 요청, 응답 모양과 기존 `SteamApiService` 동작을 바꾸지 않는다. 새 경로는 `/api/library/**`뿐이다
- 공유 스냅샷과 로그에 SteamID64, 닉네임, 아바타, 게임 목록을 남기지 않는다. 로그의 SteamID64는 끝 4자리만 남긴다
- `lang`은 `ko`, `en`(대소문자 무시), 그 밖이나 없으면 `ko`(1차 `Lang.from` 재사용)
- 지표는 0~100 정수 또는 `null`이고, 실력 점수가 아니라 성향 지표다
- 문구에 "MBTI"를 쓰지 않고, 비하 표현을 쓰지 않는다
- 주석은 한국어, ~한다/~함으로 끝내고 마침표를 쓰지 않는다. 쉬운 말로 쓰고 가운뎃점(·)을 쓰지 않는다. AI 도구 흔적을 남기지 않는다
- git 상태를 바꾸는 명령을 실행하지 않는다. 태스크 끝에 커밋 파일 목록과 한국어 커밋 메시지(`feat: ...`, 공동 작성자 줄 없음)를 보고한다
- 테스트: `backend/`에서 `./gradlew test --tests '*클래스명'`(완전한 이름 필터는 이 환경에서 동작하지 않음). 종료 코드와 `build/test-results/test/*.xml`로 판정한다. DB 테스트는 `PostgresTestBase`나 `TasteDbTestConfig` 같은 기존 공용 설정을 재사용해 Spring 컨텍스트를 늘리지 않는다

## Review Focus

- 비공개 프로필이 "게임 0개"나 503으로 섞이지 않아야 한다 → Task 2 해석 테스트
- 사용자 지정 주소 입력에 공백, 끝 슬래시, 대문자가 섞여도 해석되어야 한다 → Task 2
- 게임은 있는데 플레이 시간이 전부 0이거나 DB 매칭이 0개여도 500 없이 `limitedData: true`와 `null` 지표가 나와야 한다 → Task 4
- 공유 스냅샷 JSON 문자열에 SteamID64, 닉네임, 아바타 주소가 들어가면 안 된다 → Task 5, Task 6
- Steam이 느리거나 죽어도 요청 스레드가 무한정 묶이지 않아야 한다(시간 제한) → Task 2

---

### Task 1: API 계약 초안

**Files:** Create `C:\Users\black\Desktop\Projects\SteamGame_Recommend\remake\api-contract-library.md` (커밋 대상 아님)

- [ ] **Step 1:** 설계 4.5~4.7과 1차 계약 문서(`remake/api-contract-taste.md`)의 공통 규칙(호출 헬퍼, 오류 처리, 429 본문, `fireAndForget`)을 참조해 초안을 쓴다. 화면 흐름(입력 → 분석 → 네 카드 → 공유 → 공유 링크 열기 → 새 게임 추천은 기존 `/api/recommend/profile`), 경로별 요청과 응답 예시(초안 표시), 오류 표, 이벤트(`SHARE_CLICK`/`SHARE_INFLOW`의 `ref: "LIBRARY"`)를 담는다. 응답 필드 이름은 Task 6의 DTO와 같아야 한다

---

### Task 2: Steam 입력 해석과 라이브러리 읽기

**Files:**
- Create: `$B/domain/library/steam/SteamIdResolver.java`, `SteamLibraryParser.java`, `SteamLibraryClient.java`, `SteamLibraryResult.java`, `SteamProfile.java`, `OwnedGame.java`
- Modify: `src/main/resources/application.properties` (Steam 호출 시간 제한, 캐시 설정 키 추가)
- Test: `$T/domain/library/steam/SteamIdResolverTest.java`, `SteamLibraryParserTest.java`, `SteamLibraryClientTest.java`, 픽스처 `src/test/resources/library/steam/*.json`

**Interfaces:**
- Produces:
  - `record SteamProfile(String steamId64, String personaName, String avatarUrl)`
  - `record OwnedGame(long appid, String name, int minutesTotal, int minutesTwoWeeks, Instant lastPlayed)` (lastPlayed는 0이면 null)
  - `sealed interface SteamLibraryResult` 구현: `Found(SteamProfile profile, List<OwnedGame> games)`, `Private()`, `NotFound()`, `Empty()`, `Unavailable(String reason)`
  - `final class SteamLibraryParser` — `GetPlayerSummaries`, `GetOwnedGames` 응답 문자열을 해석하는 정적 메서드 두 개. 반환 타입은 구현자가 정하되 Step 1의 일곱 경우를 서로 구분해야 한다
  - `@Component SteamIdResolver { String resolve(String input) }` 400 `ResponseStatusException(BAD_REQUEST, "INVALID_STEAM_ID")`, 404 `ResponseStatusException(NOT_FOUND, "STEAM_PROFILE_NOT_FOUND")`
  - `@Component SteamLibraryClient { SteamLibraryResult fetch(String steamId64) }` 예외를 밖으로 던지지 않는다(전부 결과 타입으로)

- [ ] **Step 1: 해석 실패 테스트 작성**

`SteamLibraryParserTest`(픽스처 JSON 사용):
- `summaryPublic`: `communityvisibilitystate: 3` → 공개 프로필(닉네임, 아바타 `avatarfull`)
- `summaryPrivate`: `communityvisibilitystate: 1` → 비공개
- `summaryNoPlayer`: `players: []` → 없음
- `ownedGamesNormal`: games 3개 → `OwnedGame` 3개(분 단위 그대로, `rtime_last_played` 0은 null)
- `ownedGamesDetailsPrivate`: `{"response":{}}` → 비공개
- `ownedGamesZero`: `{"response":{"game_count":0}}` → 게임 없음
- `brokenJson`: 깨진 문자열 → 해석 실패(Unavailable 사유)

Step 3에서 해석 결과를 표현할 타입은 구현자가 정하되(`enum SummaryStatus` 등), 위 일곱 경우가 서로 구분되어야 한다.

`SteamIdResolverTest`(Steam 호출 부분은 `Function<String, String>` 같은 주입 가능한 조회기로 목 처리):
- `76561198000000000` → 그대로, 앞뒤 공백도 허용
- `https://steamcommunity.com/profiles/76561198000000000/`, `http://...` → 숫자
- `https://steamcommunity.com/id/Gabe/`와 `Gabe` → 조회기 결과 SteamID64. 조회기에 넘긴 이름이 `Gabe`(대소문자 유지)
- 조회 실패(`success` 42) → 404 `STEAM_PROFILE_NOT_FOUND`
- `abc def`, 빈 문자열, `7656119800000000`(16자리), 33자 이름 → 400 `INVALID_STEAM_ID`

- [ ] **Step 2: 실패 확인** (`./gradlew test --tests '*SteamLibraryParserTest' --tests '*SteamIdResolverTest'`)

- [ ] **Step 3: 구현**

`SteamLibraryClient`는 기존 `WebClient.Builder`와 `steam.api.key`를 쓰고, 요청마다 시간 제한(`app.library.steam-timeout-seconds`, 기본 5)을 건다. `GetPlayerSummaries` 먼저, 공개일 때만 `GetOwnedGames`. 결과가 `Found`면 SteamID64 키로 캐시(`app.library.steam-cache-minutes` 기본 10, 최대 1,000개). 예외는 `Unavailable`로 바꾸고 WARN 로그(SteamID64는 끝 4자리만). `ResolveVanityURL`도 같은 클라이언트에 `Optional<String> resolveVanity(String name)`으로 둔다.

- [ ] **Step 4: `SteamLibraryClientTest`** — `WebClient`에 `ExchangeFunction`을 주입하는 방식(추가 의존성 없이)으로 Steam 응답을 흉내 내 다음을 확인: 공개면 두 번 호출하고 `Found`, 비공개 요약이면 게임 목록을 부르지 않고 `Private`, 5xx면 `Unavailable`, 같은 SteamID64 두 번째 `fetch`는 HTTP를 부르지 않음(캐시), `Private`는 캐시하지 않음, 로그에 닉네임과 게임 이름이 없음

- [ ] **Step 5: 통과 확인과 커밋 제안** `feat: Steam 라이브러리 읽기와 입력 해석 추가`

---

### Task 3: 분석 문구 데이터

**Files:**
- Create: `$B/domain/library/content/LibraryContent.java`(및 하위 레코드), `LibraryContentValidator.java`, `LibraryContentLoader.java`, `src/main/resources/library/library-v1.json`
- Test: `$T/domain/library/content/LibraryContentValidatorTest.java`, `LibraryContentFileTest.java`

**Interfaces:**
- Produces:
  - `record LibraryContent(String version, Map<String, List<String>> tagGroups, Map<String, List<Phrase>> phrases)` — `tagGroups` 키: `EXPLORATION`, `STRATEGY`, `COOP`, `STORY`, `ACTION`, `RELAX`, `SIMULATION`, `PUZZLE`, `RPG`, `HORROR`, `SPORTS`, `COMPETITIVE`(다양성 계산에 쓰는 묶음 전체 12개, 앞의 셋은 지표에도 씀. 2026-11-01 Task 3 리뷰로 뒤의 넷을 추가). `phrases` 키: 장부 판정 코드 6개, 죄목 5개, 형량 5개(`SENTENCE_` + 죄목 코드), 지표 라벨 5개(`STAT_EXPLORATION` 등)
  - `record Phrase(LocalizedText text, List<String> params)` (1차 `LocalizedText` 재사용)
  - `LibraryContentLoader.content()` 기동 시 검증, 실패 시 기동을 막는다

- [ ] **Step 1: 검증기 실패 테스트** — 판정 코드 누락, 문구 후보 0개 또는 4개 이상, ko/en 빈칸, 문구에 `params`에 없는 자리표시자 `{x}`가 있음, 묶음 태그 중복, 태그 묶음이 비어 있음 → 각각 위반 메시지(`hasMessageContaining`)
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 레코드, 검증기, JSON 작성** — 판정마다 문구 후보 2~3개. 장부, 판결 문구는 설계 4.4 표의 뜻을 살려 가볍고 재치 있게, 비하 없이. 숫자는 자리표시자(`{zeroCount}`, `{ownedCount}`, `{topGame}`, `{topRatio}`, `{topTag}` 등)로 쓴다. 태그 묶음은 1차 설계 부록의 운영 태그 이름에서 고른다
- [ ] **Step 4: 파일 테스트** — 실제 JSON 로드, 모든 묶음 태그가 `src/test/resources/taste/prod-tag-names.txt`에 있음, "MBTI"와 `·` 없음, 모든 판정 코드에 문구 존재
- [ ] **Step 5: 통과 확인과 커밋 제안** `feat: 라이브러리 분석 문구와 태그 묶음 데이터 추가`

---

### Task 4: 분석 계산

**Files:**
- Create: `$B/domain/library/analysis/LibraryAnalyzer.java`, `LibraryAnalysis.java`(및 하위 레코드), `LedgerCode.java`, `CrimeCode.java`, `StatCode.java`
- Test: `$T/domain/library/analysis/LibraryAnalyzerTest.java`

**Interfaces:**
- Consumes: `OwnedGame`(Task 2), `LibraryContent`(Task 3), `Lang`, `LocalizedText`
- Produces:
  - `enum LedgerCode { STARTER, LEGENDARY_COLLECTOR, COLLECTOR, EXPLORER, PRACTICAL, BALANCED }`
  - `enum CrimeCode { PILE_OF_SHAME, ONE_GAME_DEVOTION, GENRE_LOYALIST, GENRE_NOMAD, INNOCENT }`
  - `enum StatCode { EXPLORATION, STRATEGY, COLLECTION, DIVERSITY, COOP }`
  - `record LibraryAnalysis(boolean limitedData, Stats stats, Judged ledger, Verdict verdict, List<GamerStat> gamerStats, Diagnosis diagnosis)` 하위: `Stats(int ownedCount, double totalHours, int zeroPlayCount, double zeroPlayRatio, TopGame mostPlayed, int recentGameCount, double recentHours, int matchedCount, List<TagShare> topTags)`, `TopGame(long appid, String name, double hours, double ratio)`, `TagShare(String tag, double hours, double ratio)`, `Judged(String code, int phraseIndex, Map<String, Object> params)`, `Verdict(Judged crime, Judged sentence)`, `GamerStat(StatCode code, Integer value)`, `Diagnosis(String favoriteTag, List<TopGame> concentratedGames, String hiddenTaste)`
  - `final class LibraryAnalyzer { static LibraryAnalysis analyze(List<OwnedGame> games, Map<Long, List<String>> tagsByAppid, Function<String, Double> rarity, LibraryContent content, String steamId64) }` — `phraseIndex`는 `steamId64` 해시와 판정 코드로 정한다(같은 입력이면 같은 값)

- [ ] **Step 1: 실패 테스트 작성** — 설계 4.4의 표와 식을 그대로 단언한다. 필수 테스트:
  - 장부 판정마다 경계값 안과 밖 한 쌍(예: 보유 9개 → STARTER, 10개 → STARTER 아님; 0분 비율 39%와 40%)
  - 죄목마다 경계값 한 쌍, `PILE_OF_SHAME`은 0분 게임 9개면 해당 없음
  - `noContradiction_exhaustive`: 보유 수(5, 10, 50, 299, 300, 800), 0분 비율(0, 0.14, 0.15, 0.39, 0.4, 0.5, 0.9), 최다 게임 비율(0.2, 0.5, 0.8)을 조합해 만든 라이브러리 전부에서 장부 `PRACTICAL`과 죄목 `PILE_OF_SHAME`이 함께 나오지 않음을 확인한다
  - 지표: 탐험 묶음 비율 25% → 50, 60% → 100, 매칭 0개 → 탐험, 전략, 협동, 다양성 `null`, 수집 식
  - 다양성: 한 묶음에 전부 → 0, 묶음 8개 균등 → 100
  - 진단서 숨은 취향: 최근 2주 경로와 희소도 경로, 근거 없으면 `null`
  - `limitedData`: 총 시간 0 또는 매칭 4개 → true, 매칭 5개 이상이고 시간 있으면 false
  - 재현성: 같은 입력 두 번 → 같은 `phraseIndex`
  - 게임 0개 입력은 호출하지 않는다는 전제를 문서화하고, 들어오면 `IllegalArgumentException`
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현** (순수 함수, 반올림은 시간 소수 첫째 자리, 비율 소수 셋째 자리)
- [ ] **Step 4: 통과 확인과 커밋 제안** `feat: 라이브러리 장부, 판결문, 성향 지표, 진단서 계산 추가`

---

### Task 5: V9 마이그레이션, 공유 저장, 이벤트 확장

**Files:**
- Create: `src/main/resources/db/migration/V9__library_share_and_event.sql`, `$B/domain/library/share/LibraryShareRepository.java`(JdbcTemplate), `LibraryShareService.java`
- Modify: `$B/domain/event/entity/FunnelEventType.java`(`LIBRARY_COMPLETE`), `$B/domain/event/service/FunnelEventService.java`(SHARE_CLICK, SHARE_INFLOW의 ref에 `LIBRARY` 허용)
- Test: `$T/domain/library/share/LibraryShareRepositoryTest.java`, `LibraryShareServiceTest.java`, 기존 `FunnelEventServiceTest`와 마이그레이션 테스트 보강

**Interfaces:**
- Produces:
  - `LibraryShareService.save(String payloadJson) -> String shareId` (22자 URL 안전 임의 문자열, `SecureRandom` 16바이트 base64url), `Optional<String> find(String shareId)`(만료 제외), `@Scheduled(cron = "0 30 4 * * *") int deleteExpired()`(INFO 로그로 개수)
  - 보관 기간 `app.library.share-days` 기본 90
- [ ] **Step 1: 실패 테스트** — V9: `funnel_event`에 `LIBRARY_COMPLETE` 삽입 성공, 모르는 값 거절, `library_share` 삽입과 만료 인덱스 존재. 저장소: 저장 후 조회, 만료된 것 조회 안 됨, `deleteExpired`가 만료된 것만 지움. 이벤트: `SHARE_CLICK, "LIBRARY", LINK` 통과, `SHARE_INFLOW, "LIBRARY", null` 통과, `FEATURE_SELECT, "LIBRARY"`는 기존대로 통과, `LIBRARY_COMPLETE`는 클라이언트 요청으로 받지 않음(400)
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현** — V9는 1차 V8과 같은 방식(CHECK를 지우고 다시 걸기, `NOT VALID` 후 `VALIDATE`, 주석에 한 트랜잭션이라 잠금이 줄지 않는다는 사실 명시). `payload`는 JSONB
- [ ] **Step 4: 통과 확인과 커밋 제안** `feat: 라이브러리 공유 저장과 분석 완료 이벤트 추가`

---

### Task 6: 서비스, 컨트롤러, 요청 제한

**Files:**
- Create: `$B/domain/library/LibraryAnalysisService.java`, `$B/controller/LibraryController.java`, `$B/domain/library/dto/` 아래 `AnalyzeRequest`, `AnalyzeResponse`, `ShareRequest`, `ShareResponse`, 그리고 응답 하위 뷰 레코드, `LibraryViewMapper`
- Modify: `$B/infra/web/RateLimitFilter.java`(`/api/library/analyze` → `library-analyze` 10, `/api/library/share` → `library-share` 20, POST만 해당되게 경로 일치), `application.properties`
- Test: `$T/controller/LibraryControllerTest.java`, `$T/domain/library/LibraryAnalysisServiceTest.java`

**Interfaces:**
- Consumes: Task 2~5 전부, `TagRepository.findGameTagMapping`, `TagRarityCache.rarity`, `FunnelEventService.record`
- Produces (응답 JSON 필드 이름은 레코드 필드 그대로, Task 1 계약과 같아야 함):
  - `record AnalyzeRequest(@NotBlank @Size(max = 200) String steamId, @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String clientId)`
  - `record AnalyzeResponse(String analysisId, boolean limitedData, ProfileView profile, StatsView stats, PhraseView ledger, VerdictView verdict, List<GamerStatView> gamerStats, DiagnosisView diagnosis)`
  - `record PhraseView(String code, String text, Map<String, Object> params)`, `record VerdictView(PhraseView crime, PhraseView sentence)`, `record GamerStatView(String code, Integer value, String label)`, `record ProfileView(String personaName, String avatarUrl)`, `StatsView`, `DiagnosisView`(Task 4 레코드와 같은 필드)
  - `record ShareRequest(@NotBlank String analysisId)`, `record ShareResponse(String shareId)`
  - 공유 조회 응답은 `AnalyzeResponse`에서 `analysisId`와 `profile`을 뺀 `SharedAnalysisResponse`
- [ ] **Step 1: 실패 테스트** — 컨트롤러 슬라이스(1차 `TasteControllerTest` 형식): 정상 분석 200과 키 집합, `limitedData`, 오류 매핑(400 INVALID_STEAM_ID, 404 STEAM_PROFILE_NOT_FOUND, 403 LIBRARY_PRIVATE, 422 LIBRARY_EMPTY, 503), 완료 이벤트 기록(clientId 있을 때만), 공유 저장 200, 만료 analysisId 404 ANALYSIS_EXPIRED, 공유 조회 200과 404 SHARE_NOT_FOUND, `?lang=en`. 서비스: 공유 스냅샷 JSON에 SteamID64, 닉네임, 아바타 주소 문자열이 없음, 분석 결과 30분 캐시(가짜 시계)
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현** — 분석 결과 캐시는 Caffeine(30분, 최대 10,000개). 503은 `ResponseStatusException(SERVICE_UNAVAILABLE, "STEAM_UNAVAILABLE")`(처리기가 5xx 문구를 가린다는 점을 주석에 적음)
- [ ] **Step 4: 요청 제한 등록과 테스트** — 11번째 분석 요청 429(1차 `RateLimitTasteEventsTest` 형식)
- [ ] **Step 5: 전체 테스트와 커밋 제안** `feat: 라이브러리 분석 API와 공유 API 추가`

---

### Task 7: 실제 응답으로 계약 문서 완성

- [ ] **Step 1:** 1차와 같은 방식으로 임시 전체 앱 테스트를 만들어(Steam 호출은 `SteamLibraryClient`를 목 빈으로 바꿔 공개, 비공개, 게임 없음, 장애 응답을 흉내) 실제 HTTP 응답을 `remake/contract-responses-library/`에 저장하고 임시 테스트를 지운다
- [ ] **Step 2:** 계약 문서의 예시를 실제 응답으로 바꾸고, 독립 검토를 받아 반영한다

## 완료 전 리뷰 (프로젝트 정책)

OCR 위임 리뷰와 보강 리뷰(테스트, 오류 처리, 타입), 브랜치 전체 최종 검토, 공식 문서로 최신 API 확인(context7 연결 실패 시).
