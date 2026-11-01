# Steam 라이브러리 분석 백엔드 설계 (2D 리뉴얼 2차)

작성일: 2026-11-01
근거 코드: `tember8003/SteamGame_Recommend` main, eecc008 (1차 취향 테스트 반영)
요구사항 원문: `remake/prompt.md` PART 3, 5
작업 위치: 워크트리 `SteamGame_Recommend-library`, 브랜치 `feature/library-analysis-api`

사용자가 질문 없이 바로 진행하기로 해서, 이 문서의 결정은 모두 작성자 판단이다(9절에 목록).

## 1. 의도

랜딩 오른쪽 기록관에서 사용자가 Steam ID를 넣으면, 게임 속 NPC가 라이브러리를 분석하고 판정하는 결과를 돌려준다. 일반 통계 화면이 아니라 "장부, 판결문, 능력치, 진단서" 네 장의 재미있는 카드가 목표다. 문구는 실제 데이터 조건을 만족할 때만 나오고, 같은 데이터에서 서로 모순된 판정이 나오지 않아야 한다. 결과는 Steam ID 없이 공유할 수 있어야 한다.

프론트는 1차처럼 다른 작업자(GPT)가 만들므로, API 계약 문서가 핵심 산출물이다.

## 2. 목표, 비목표, 반목표

목표

- Steam ID(17자리 숫자, 프로필 주소, 사용자 지정 주소 이름)를 받아 라이브러리를 분석한다
- 비공개 프로필, 존재하지 않는 프로필, 게임 없음, Steam 장애를 서로 다른 오류로 돌려준다
- 기본 통계, 장부 판정, 판결문, 게이머 성향 지표 5개, 취향 진단서를 계산한다
- 분석 결과를 Steam ID와 닉네임 없이 공유 링크로 저장하고, 공유 링크로 다시 볼 수 있게 한다
- 분석 완료 이벤트를 남긴다

비목표

- 프론트 화면, 카드 이미지 생성(프론트 몫)
- 진단서의 "좋아할 만한 새로운 게임"을 새로 계산하는 일. 기존 `POST /api/recommend/profile`을 프론트가 부른다(기존 추천 엔진 재사용)
- 기존 `SteamApiService`와 기존 추천 API의 동작 변경
- 구매 금액, 결제 내역, 실력 추정(제공되지 않는 데이터)

반목표(동작해도 실패로 본다)

- 비공개 프로필이 "게임 0개"로 판정되는 것
- 공유 링크나 공유 저장소에 Steam ID, 닉네임, 아바타가 남는 것
- 같은 결과에 "실속파"와 "수집만 하는 죄"처럼 모순된 판정이 함께 나오는 것
- 지표를 실력 점수처럼 표현하는 것
- 비하, 개인정보를 드러내는 문구
- 서버 로그에 사용자의 게임 목록이 남는 것

## 3. 코드에서 확인한 사실

- `SteamApiService.getDetailedPlaytimes`가 `GetOwnedGames`(`include_appinfo=true`, `include_played_free_games=true`)로 게임별 총 플레이 시간(분), 최근 2주 시간, 마지막 플레이 시각을 받는다. 게임 이름(`name`)도 응답에 있지만 버린다
- 같은 서비스의 대체 처리(fallback)가 모든 예외를 빈 목록, 빈 맵으로 바꾼다. 비공개, 게임 없음, 장애를 구분할 수 없다
- `GetPlayerSummaries`로 닉네임과 계정 생성일을 받는다. 공개 범위(`communityvisibilitystate`)는 읽지 않는다
- Steam ID 검증은 17자리 숫자만 받는다
- `TagRepository.findGameTagMapping(appIds)`로 appid별 태그 이름을 한 번에 읽을 수 있다
- `TagRarityCache`(1차)가 태그별 희소도를 들고 있다
- `FunnelEventType`과 `funnel_event.event_type` CHECK에 라이브러리 완료 이벤트가 없다. `SHARE_CLICK`, `SHARE_INFLOW`의 `ref`는 유형 코드만 허용한다
- Caffeine이 이미 의존성에 있다(`RateLimitFilter`)

## 4. 설계

### 4.1 Steam 입력 해석

`SteamIdResolver`가 입력을 SteamID64로 바꾼다. 앞뒤 공백은 지운다.

- 17자리 숫자(7656로 시작) → 그대로
- `https://steamcommunity.com/profiles/<17자리>`(끝 슬래시, http 허용) → 숫자 부분
- `https://steamcommunity.com/id/<이름>` 또는 이름만(영문, 숫자, `_`, `-`, 2~32자) → Steam `ISteamUser/ResolveVanityURL`로 변환. 응답 `success`가 1이 아니면 404 `STEAM_PROFILE_NOT_FOUND`
- 그 밖의 형식 → 400 `INVALID_STEAM_ID`

### 4.2 라이브러리 읽기

`SteamLibraryClient`(새 클래스, 기존 서비스와 별개)가 두 번 부른다. 대체 처리로 예외를 삼키지 않는다.

1. `GetPlayerSummaries`: 플레이어가 없으면 `NOT_FOUND`. `communityvisibilitystate`가 3(공개)이 아니면 `PRIVATE`. 닉네임과 아바타 주소를 읽는다
2. `GetOwnedGames`(`include_appinfo=true`, `include_played_free_games=true`): `response`에 `games`와 `game_count`가 모두 없으면 게임 세부 정보가 비공개라 `PRIVATE`. `game_count`가 0이면 `EMPTY`. 아니면 게임 목록(appid, 이름, 총 분, 최근 2주 분, 마지막 플레이 시각)

HTTP 오류, 시간 초과, 파싱 실패는 `UNAVAILABLE`이다. 응답 해석은 순수 함수(`SteamLibraryParser`)로 분리해 JSON 문자열 픽스처로 테스트한다.

결과는 `SteamLibraryResult`(sealed: `Found(profile, games)`, `Private`, `NotFound`, `Empty`, `Unavailable(cause)`)다.

Steam API 키 사용량을 아끼고 같은 사람이 새로고침해도 Steam을 다시 부르지 않도록, `Found` 결과를 SteamID64 키로 10분 동안 메모리에 둔다(Caffeine, 게임 수 합계 200,000개까지). 비공개, 없음, 게임 0개는 2분 동안 기억한다(같은 계정을 반복 조회할 때 Steam을 다시 부르지 않게, 대신 공개로 바꾼 직후 최대 2분은 비공개로 나온다). 장애(`Unavailable`)는 기억하지 않는다. 동시에 Steam을 부르는 요청은 8개까지로 제한하고, 자리를 얻지 못하면 `Unavailable`(503)이다. 응답 본문은 16MB까지 받는다(게임 수천 개 라이브러리). 2026-11-01 리뷰 반영

로그에는 게임 목록과 닉네임을 남기지 않는다. SteamID64는 끝 4자리만 남기고 가린다.

### 4.3 분석 문구 데이터

판정 기준과 문구는 버전 붙은 JSON 리소스 `library/library-v1.json`에 둔다(1차와 같은 방식, 기동 시 검증, 틀리면 기동 막음). 담는 것:

- 태그 묶음 정의: 성향 지표에 쓰는 묶음별 Steam 태그 목록(탐험, 전략, 협동 등). 태그 이름은 운영 태그 목록 사본과 대조하는 테스트로 고정한다(1차의 `prod-tag-names.txt` 재사용)
- 묶음은 12개다: 탐험, 전략, 협동, 서사, 액션, 휴식, 시뮬레이션, 퍼즐, 롤플레잉, 공포, 스포츠와 레이싱, 경쟁 대전. 큰 장르가 묶음에 없으면 그 장르만 하는 사용자의 시간이 판정에서 빠지므로 빠짐없이 둔다
- 태그 묶음마다 한국어, 영어 이름(`groupLabels`, 예: STRATEGY → 전략 / Strategy). 한 태그는 한 묶음에만 들어간다(다양성 계산에서 같은 시간이 두 번 잡히지 않게)
- `Action`, `Adventure`, `Casual`, `Indie`, `Singleplayer`, `Simulation`, `Multiplayer`처럼 대부분의 게임에 붙은 태그는 어느 묶음에도 넣지 않는다(넣으면 대부분의 사용자가 그 묶음에 몰린다). 제외 목록과 이유를 JSON에 함께 적는다
- 판정별 문구: 한국어, 영어. 숫자가 들어가는 자리는 `{zeroCount}` 같은 이름 있는 자리표시자로 쓰고, 서버가 채우지 않고 `params`로 함께 내려준다(프론트가 언어별로 채움, 1차의 `ExplanationItem` 방식과 같음). 문구 후보는 판정마다 2~3개이고, 같은 SteamID64에는 항상 같은 후보가 나오게 고른다(SteamID64 해시로 선택, 재현 가능)

판정 조건(임계값)은 코드에 상수로 둔다. 조건은 테스트로 고정해야 하는 논리라 JSON에 두지 않는다.

### 4.4 분석 계산

`LibraryAnalyzer`는 순수 함수다(DB, 시간, 난수 없음). 지금 계산은 현재 시각을 쓰지 않는다. 입력은 게임 목록, 게임별 태그, 태그 희소도, 기준 시각이다.

기본 통계

- 보유 게임 수, 총 플레이 시간(시간, 소수 첫째 자리), 0분 게임 수와 비율, 가장 오래 한 게임(이름, 시간, 전체 대비 비율), 최근 2주에 한 게임 수와 시간, DB와 매칭된 게임 수
- 태그별 시간: 매칭된 게임마다 그 게임 시간을 게임 태그 수로 나눠 각 태그에 더한다. 상위 태그 10개와 전체 대비 비율

장부 판정(A), 위에서부터 처음 맞는 하나

| 판정 | 조건 |
|---|---|
| `STARTER` | 보유 10개 미만 |
| `LEGENDARY_COLLECTOR` | 보유 300개 이상이고 0분 비율 50% 이상 |
| `COLLECTOR` | 0분 비율 40% 이상 |
| `EXPLORER` | 플레이한 게임 중 1~5시간 게임 비율 40% 이상 |
| `PRACTICAL` | 0분 비율 15% 미만 |
| `BALANCED` | 그 밖 |

판결문(B), 위에서부터 처음 맞는 하나

| 죄목 | 조건 |
|---|---|
| `PILE_OF_SHAME`(모으기만 하는 죄) | 0분 비율 40% 이상이고 0분 게임 10개 이상 |
| `ONE_GAME_DEVOTION`(한 게임에 인생을 바친 죄) | 가장 오래 한 게임이 총 시간의 50% 이상이고 총 100시간 이상 |
| `GENRE_LOYALIST`(한 장르만 파는 죄) | 상위 태그 묶음이 총 시간의 60% 이상. 문구는 묶음 이름 `{topGroup}`과 묶음 비율 `{topGroupRatioPct}`를 쓴다 |
| `GENRE_NOMAD`(장르를 떠도는 죄) | 다양성 지표 80 이상 |
| `INNOCENT`(무죄) | 그 밖 |

모순 방지: 장부의 `PRACTICAL`은 0분 비율 15% 미만이고 판결의 `PILE_OF_SHAME`은 40% 이상이라 함께 나올 수 없다. 테스트에서 판정 조합 표를 전수로 확인한다(조건 경계값 조합 생성).

판결문에는 죄목과 함께 판결(형량) 문구를 짝으로 둔다(예: "다음 할인 때 한 개만 구매할 것").

게이머 성향 지표(C), 0~100 정수, "실력이 아니라 플레이 기록 기반 성향 지표"

- 탐험, 전략, 협동: 해당 태그 묶음이 태그별 시간에서 차지하는 비율을 0~100으로 바꾼다. 묶음 비율 50% 이상이면 100이 되도록 2배 하고 100에서 자른다
- 수집: `round(60 × 0분 비율 + 40 × min(1, 보유 수 / 500))`
- 다양성: 태그 묶음별 시간 분포의 정규화 엔트로피 × 100(묶음 하나에 다 몰리면 0, 고르게 퍼지면 100)
- 매칭된 게임이 없거나 총 시간이 0이면 탐험, 전략, 협동, 다양성은 `null`(근거 없음)로 둔다

취향 진단서(D)

- 가장 좋아하는 장르: 태그별 시간 1위 태그
- 시간이 몰린 게임: 시간 상위 3개(이름, 시간, 비율)
- 숨은 취향: 최근 2주 시간 기준 상위 5개 태그 중 전체 상위 5개에 없는 태그. 없으면 전체 6~10위 태그 가운데 희소도가 가장 높은 태그(가장 좋아하는 장르와 겹치지 않게 상위 5개는 뺀다). 근거가 없으면 `null`

매칭된 게임은 제외 태그를 뺀 태그가 하나 이상 있는 게임이다. 이름이 빈 게임은 "App {appid}"로 보인다.

데이터 부족 표시: 플레이 시간이 전부 0이거나, DB와 매칭된 게임이 5개 미만이면 `limitedData: true`를 내려준다. 판정은 계산하되 프론트가 "기록이 적어 판정이 가벼울 수 있다"는 안내를 붙인다.

### 4.5 공유 스냅샷

- 분석 응답에 `analysisId`(임의 UUID)를 넣고, 분석 결과를 30분 동안 메모리에 둔다(Caffeine)
- `POST /api/library/share {analysisId}` → 결과에서 SteamID64, 닉네임, 아바타를 뺀 스냅샷을 `library_share` 테이블에 저장하고 `shareId`(URL에 쓰기 좋은 22자 임의 문자열)를 돌려준다. 30분이 지나 `analysisId`가 없으면 404 `ANALYSIS_EXPIRED`
- `GET /api/library/share/{shareId}` → 저장된 스냅샷. 없거나 만료되면 404 `SHARE_NOT_FOUND`
- 스냅샷은 90일 뒤 만료되고, 매일 04:30에 만료된 것을 지운다(지운 개수 INFO 로그)
- 공유는 사용자가 공유 버튼을 눌러야만 저장된다. 분석만 해서는 아무것도 저장하지 않는다

### 4.6 이벤트

- 분석 성공 시 서버가 `LIBRARY_COMPLETE`(ref null)를 남긴다(clientId가 있을 때)
- 라이브러리 결과 공유는 기존 `SHARE_CLICK`, `SHARE_INFLOW`의 `ref`에 `LIBRARY`를 허용해 남긴다
- V9 마이그레이션: `funnel_event.event_type` CHECK에 `LIBRARY_COMPLETE` 추가, `library_share` 테이블(share_id PK, payload JSONB, created_at, expires_at, 만료 인덱스)

### 4.7 API

| 메서드, 경로 | 용도 |
|---|---|
| `POST /api/library/analyze?lang=` | `{steamId, clientId}` → 분석 결과 |
| `POST /api/library/share` | `{analysisId}` → `{shareId}` |
| `GET /api/library/share/{shareId}?lang=` | 공유 스냅샷 |

분석 응답(요지): `analysisId`, `limitedData`, `profile{personaName, avatarUrl, steamId64}`(본인 화면용, 공유에는 없음. `steamId64`는 진단서의 새 게임 추천을 기존 `/api/recommend/profile`로 부를 때 쓴다), `stats{ownedCount, totalHours, zeroPlayCount, zeroPlayRatio, mostPlayed{appid, name, hours, ratio}, recentGameCount, recentHours, matchedCount, topTags[{tag, hours, ratio}]}`, `ledger{code, text, params}`, `verdict{crime{code, text, params}, sentence{code, text, params}}`, `gamerStats[{code, value, label}]`, `diagnosis{favoriteTag, concentratedGames[...], hiddenTaste}`.

`text`는 `lang`에 맞는 문구 원문(자리표시자 포함), `params`는 채울 값이다.

오류

| 상태 | 메시지 | 언제 |
|---|---|---|
| 400 | `INVALID_STEAM_ID` | 입력 형식이 맞지 않음 |
| 404 | `STEAM_PROFILE_NOT_FOUND` | 사용자 지정 주소를 찾지 못함, 플레이어 없음 |
| 403 | `LIBRARY_PRIVATE` | 프로필 또는 게임 세부 정보가 비공개 |
| 422 | `LIBRARY_EMPTY` | 게임 0개 |
| 503 | (기존 처리기가 5xx 문구를 가림) | Steam 장애, 시간 초과. 프론트는 상태 코드 503으로 판단 |
| 404 | `ANALYSIS_EXPIRED`, `SHARE_NOT_FOUND` | 공유 관련 |
| 429 | (요청 제한 본문) | 분석 IP당 분당 10번, 공유 저장 20번 |

### 4.8 테스트

- 입력 해석: 형식별 정상, 거절, 사용자 지정 주소 변환 성공과 실패(Steam 호출은 목)
- 응답 해석: 공개, 비공개(공개 범위), 게임 세부 비공개(빈 response), 게임 0개, 플레이어 없음, 깨진 JSON을 JSON 픽스처로
- 분석: 판정별 경계값, 판정 조합 전수 확인(모순 조합이 나오지 않음), 지표 계산과 `null` 조건, 숨은 취향 두 경로, 문구 후보 선택의 재현성
- 문구 데이터: 판정 코드마다 ko, en 문구 존재, 자리표시자가 params 이름과 맞음, 태그 묶음의 태그가 운영 태그 목록에 있음, "MBTI"와 가운뎃점 없음
- 공유: 스냅샷에 SteamID64, 닉네임, 아바타가 없음(JSON 문자열 검사), 만료, 정리 작업
- 컨트롤러: 경로별 정상과 오류 상태, 메시지, 요청 제한 등록, 완료 이벤트 기록
- 마이그레이션: V9 제약과 테이블

## 5. 배포와 운영

- 새 경로만 추가한다. 기존 API 모양은 그대로다(`/api/metrics/overview`도 영향 없음)
- 롤백: 앱만 되돌리면 된다. 이전 앱은 `LIBRARY_COMPLETE` 이벤트를 읽지 않으므로 지표 화면도 깨지지 않는다
- Steam API 일일 호출 한도 안에서 쓰도록 10분 캐시와 IP당 분당 10번 제한을 둔다. 분석 한 번에 Steam 호출은 2~3번이다

## 6. 사용자가 정한 것

- 2차를 바로 진행한다, 질문은 건너뛴다
- 작업은 워크트리 `SteamGame_Recommend-library`, 브랜치 `feature/library-analysis-api`에서 한다
- 태스크마다 커밋을 안내받는다

## 9. 작성자 판단으로 정한 것

- 사용자 지정 주소와 프로필 주소도 입력으로 받는다(SteamID64를 모르는 사용자가 많다)
- 기존 `SteamApiService`를 고치지 않고 새 클라이언트를 만든다
- 진단서의 새 게임 추천은 기존 프로필 추천 API를 프론트가 부른다
- 판정 임계값(표의 수치)과 지표 식
- 문구 후보는 SteamID64 해시로 골라 같은 사람에게 같은 문구가 나온다
- 공유는 버튼을 눌렀을 때만 저장하고, 닉네임과 아바타는 공유에서 뺀다. 공유 보관 90일
- Steam 결과 10분 캐시(게임 수 합계 상한), 비공개와 없음과 게임 0개 2분 기억, Steam 동시 호출 8개 제한, 분석 결과 30분 캐시
- 분석 응답 `profile`에 `steamId64`를 넣는다(사용자가 입력한 본인 ID, 공유와 로그에는 없음)
- 공유 스냅샷은 `{"v":1,"analysis":...}` 형식이고 JSON 이름을 고정한다. 읽을 수 없는 스냅샷은 404, 문구 결함은 500
- 오류 상태 코드(403, 422, 503)
