# Steam 라이브러리 분석 API 계약

작성일: 2026-11-01
상태: 구현 완료. 아래 예시는 2026-11-01에 테스트 환경(시험용 카탈로그, Steam API 응답은 가짜 서버가 대신함)에서 실제 서버를 띄워 받은 응답이다. 게임과 숫자는 시험용이지만 필드 이름, 상태 코드, 헤더, 오류 모양은 운영과 같다. 경로별 전체 응답 원문은 `remake/contract-responses-library/` 폴더에 있다.

응답 파일은 작은 수정을 넣기 전에 받은 것이다. 수정 뒤 추가된 동작은 다음과 같고, 필드 이름은 바뀌지 않았다. 단 `profile.steamId64`는 필드가 새로 생겼고 응답 파일에는 아직 없다.

- 같은 `analysisId`로 공유를 반복하면 같은 `shareId`가 온다
- 비공개, 프로필 없음, 보유 게임 없음 결과를 서버가 2분 동안 기억한다
- Steam 호출이 몰려 자리를 못 얻어도 503이 온다
- 분석 응답의 `profile`에 `steamId64`가 들어 있다

랜딩 오른쪽 기록관 화면을 위한 API다. 공통 규칙(호출 방식, `Content-Type`, `clientId`, `lang`, 오류 본문 모양, 429 본문, 호출 헬퍼 `tasteRequest`, `fireAndForget`)은 `api-contract-taste.md` 1절을 그대로 따른다. 이 문서에는 다른 점만 적는다.

## 1. 화면 흐름

| 순서 | 사용자 행동 | 프론트가 부르는 API |
|---|---|---|
| 1 | 기록관 클릭 | `POST /api/events` `{type: "FEATURE_SELECT", ref: "LIBRARY"}` |
| 2 | Steam ID 입력 후 분석 | `POST /api/library/analyze?lang=ko` |
| 3 | 결과 화면(장부, 판결문, 능력치, 진단서) | 2번 응답으로 그린다 |
| 4 | 진단서의 "좋아할 만한 새 게임" | 기존 `POST /api/recommend/profile`에 2번 응답의 `profile.steamId64`를 `steamId`로 넣어 부른다(아래 설명) |
| 5 | 공유 버튼 | `POST /api/library/share` → 받은 `shareId`로 링크를 만들고 `POST /api/events` `{type: "SHARE_CLICK", ref: "LIBRARY", channel: "LINK" 또는 "IMAGE"}` |
| 6 | 친구가 공유 링크로 들어옴 | `GET /api/library/share/{shareId}?lang=ko`로 그리고, 주소에 `?s=1`이 있으면 `{type: "SHARE_INFLOW", ref: "LIBRARY"}`를 한 번 보낸 뒤 `history.replaceState`로 주소에서 `s=1`을 지운다 |

- 4번 요청 본문은 `{ "steamId": "<profile.steamId64>", "clientId": "..." }`이고 나머지 필드(`review` 정수, `korean_check`, `free_check`, `excludeAppids`)는 기존 프론트 코드가 보내는 값을 그대로 쓴다. `steamId`만 필수다. 이 API는 17자리 숫자만 받으므로 사용자가 입력한 주소나 이름을 넣으면 500이 난다. 반드시 `profile.steamId64`를 쓴다. 이 API는 IP당 분당 30번 제한이고 오류 모양은 기존 계약을 따른다(이 문서의 4절과 다르다)
- 분석 완료 이벤트(`LIBRARY_COMPLETE`)는 분석 요청에 `clientId`가 있을 때만 서버가 2번에서 남긴다. 프론트가 직접 보내지는 않는다. 집계가 필요하므로 2번 요청에 `getClientId()` 값을 항상 넣는다
- 공유 링크 예시: `/ko/library/share/{shareId}?s=1`. 링크와 공유 저장소에는 Steam ID, 닉네임, 아바타가 없다. 단 공유 결과에는 게임 이름과 플레이 시간이 들어 있고, 링크를 아는 사람은 누구나 90일 동안 읽을 수 있다. 공유 전 확인 문구에 이 내용을 넣는다
- 5번에서 링크를 공유할 때는 `POST /api/library/share`가 필요하다. 링크가 들어 있지 않은 이미지만 저장할 때는 이 호출이 필요 없고 `SHARE_CLICK`만 보낸다
- 분석 결과는 DB에 저장하지 않는다. 공유 버튼을 눌러야 DB에 저장된다. 서버 메모리에는 분석 결과가 30분, Steam에서 읽은 결과가 10분 남고, `LIBRARY_COMPLETE` 이벤트(`clientId`, 시각)는 DB에 남는다

## 2. Steam ID 입력

다음 형식을 모두 받는다. 앞뒤 공백은 서버가 지운다.

- 17자리 숫자: `7656`으로 시작하는 17자리 숫자(예: `76561198000000000`). 숫자만 입력했는데 `7656`으로 시작하는 17자리가 아니면 400 `INVALID_STEAM_ID`다
- 프로필 주소: `https://steamcommunity.com/profiles/76561198000000000`
- 사용자 지정 주소: `https://steamcommunity.com/id/myname` 또는 `myname`

세부 규칙은 다음과 같다.

- 주소 앞의 `https://`, `www.`와 끝 슬래시, 쿼리는 있어도 된다
- 사용자 지정 이름은 영문, 숫자, `_`, `-`로 2자에서 32자까지다. 한글, 공백, 1글자는 400 `INVALID_STEAM_ID`다
- 숫자만으로 된 사용자 지정 이름은 ID로 취급된다. 이런 이름은 `https://steamcommunity.com/id/숫자` 주소 형태로 넣어야 한다
- 없는 사용자 지정 이름은 404 `STEAM_PROFILE_NOT_FOUND`, 이름 조회 중 Steam 장애는 503이다
- 프론트는 사전 검증을 생략하고 서버 판정에 맡기거나, 위 규칙과 같은 정규식만 쓴다

입력 화면에 "Steam 프로필과 게임 세부 정보가 공개여야 분석할 수 있다"는 안내를 둔다.

## 3. API

### 3.1 POST /api/library/analyze?lang=ko

요청

```json
{ "steamId": "https://steamcommunity.com/id/myname", "clientId": "3f2c9a1e-0000-4000-8000-000000000001" }
```

- `steamId`는 필수이고 공백만이거나 200자를 넘으면 400 `VALIDATION_ERROR`다
- `clientId`는 선택이다. 값이 없으면 키 자체를 빼고 보낸다. 빈 문자열, 64자 초과, 영문, 숫자, `-`, `_` 밖의 문자가 있으면 400 `VALIDATION_ERROR`다
- 분석은 누를 때마다 새 `analysisId`가 생긴다. 응답이 올 때까지 버튼을 잠근다
- 처리 시간은 보통 1~3초이지만 Steam이 느리면 30초 안팎까지 걸린다. 프론트 요청 시간 제한은 35초 이상으로 두고 로딩 표시를 보인다

응답 200 (실제 응답, `topTags`는 3개만 남기고 줄였고 `profile`의 닉네임과 아바타는 예시 값으로 바꿨다)

```json
{
  "analysisId": "11af5de5-1b63-4569-bde2-696752565a85",
  "limitedData": false,
  "profile": {
    "personaName": "닉네임",
    "avatarUrl": "https://avatars.steamstatic.com/....jpg",
    "steamId64": "76561198000000000"
  },
  "stats": {
    "ownedCount": 25,
    "totalHours": 704.0,
    "zeroPlayCount": 10,
    "zeroPlayRatio": 0.4,
    "mostPlayed": {
      "appid": 292030,
      "name": "The Witcher 3: Wild Hunt",
      "hours": 150.0,
      "ratio": 0.213
    },
    "recentGameCount": 3,
    "recentHours": 14.0,
    "matchedCount": 25,
    "topTags": [
      {
        "tag": "Open World",
        "hours": 93.0,
        "ratio": 0.132
      },
      {
        "tag": "RPG",
        "hours": 89.0,
        "ratio": 0.126
      },
      {
        "tag": "Story Rich",
        "hours": 78.2,
        "ratio": 0.111
      }
    ]
  },
  "ledger": {
    "code": "COLLECTOR",
    "text": "안 해 본 게임이 {zeroCount}개로 전체의 {zeroRatioPct}%입니다. 수집가의 기질이 보입니다.",
    "params": {
      "zeroCount": 10,
      "zeroRatioPct": 40
    }
  },
  "verdict": {
    "crime": {
      "code": "PILE_OF_SHAME",
      "text": "게임은 모으는데 플레이는 안 하는 죄",
      "params": {}
    },
    "sentence": {
      "code": "SENTENCE_PILE_OF_SHAME",
      "text": "다음 할인 때 한 개만 구매할 것",
      "params": {}
    }
  },
  "gamerStats": [
    {
      "code": "EXPLORATION",
      "value": 36,
      "label": "탐험 성향"
    },
    {
      "code": "STRATEGY",
      "value": 50,
      "label": "전략 성향"
    },
    {
      "code": "COLLECTION",
      "value": 26,
      "label": "수집 성향"
    },
    {
      "code": "DIVERSITY",
      "value": 77,
      "label": "취향 다양성"
    },
    {
      "code": "COOP",
      "value": 4,
      "label": "협동 성향"
    }
  ],
  "diagnosis": {
    "favoriteTag": "Open World",
    "concentratedGames": [
      {
        "appid": 292030,
        "name": "The Witcher 3: Wild Hunt",
        "hours": 150.0,
        "ratio": 0.213
      },
      {
        "appid": 289070,
        "name": "Sid Meier's Civilization VI",
        "hours": 120.0,
        "ratio": 0.17
      },
      {
        "appid": 1091500,
        "name": "Cyberpunk 2077",
        "hours": 90.0,
        "ratio": 0.128
      }
    ],
    "hiddenTaste": "Cozy"
  }
}
```

문구 채우기

- `text`는 문구 원문이고 `{이름}` 자리표시자가 채워지지 않은 채 온다. 프론트가 같은 객체의 `params`에서 같은 이름의 값을 찾아 바꿔 넣는다(예: `{zeroCount}` → `params.zeroCount`)
- 문구는 정규식 `\{(\w+)\}`로 한 번에 치환한다. 이름을 하나씩 순서대로 replace하면 게임 이름 안의 `{`가 다시 치환될 수 있다. 치환한 문구는 HTML이 아닌 텍스트로 화면에 넣는다
- 자리표시자가 없는 문구는 `params`가 `{}`다. 자리표시자가 있는 문구는 필요한 값이 `params`에 항상 있다. 공유 응답(3.3)에서는 쓰이지 않는 값이 섞여 있을 수 있으므로 `text`에 있는 이름만 찾는다
- 문구 끝 마침표는 장부 문구에만 있고 죄목과 판결 문구에는 없다
- 같은 Steam ID에는 항상 같은 문구가 나온다. 주소나 사용자 지정 이름으로 넣어도 같은 ID로 풀리면 같다

자리표시자 이름은 다음 열 가지가 나올 수 있다.

| 이름 | 타입 | 내용 |
|---|---|---|
| `ownedCount` | 정수 | 보유 게임 수 |
| `zeroCount` | 정수 | 플레이 시간이 0인 게임 수 |
| `zeroRatioPct` | 정수 | 위 비율의 백분율 |
| `shortPlayedRatioPct` | 정수 | 플레이한 게임 중 1시간 이상 5시간 이하인 게임의 백분율 |
| `totalHours` | 실수 | 총 플레이 시간 |
| `topGame` | 문자열 | 가장 오래 한 게임 이름. 이름이 비면 `App 12345` 형태 |
| `topRatioPct` | 정수 | 위 게임이 총 시간에서 차지하는 백분율 |
| `topGameHours` | 실수 | 위 게임의 플레이 시간 |
| `topGroup` | 문자열 | 가장 많이 한 장르 묶음의 표시 이름 |
| `topGroupRatioPct` | 정수 | 위 묶음이 차지하는 백분율 |

- `topGroup`은 요청의 `lang`에 맞는 표시 이름으로 이미 바뀌어 온다(ko 예: `탐험`, `스포츠와 레이싱`, en 예: `Exploration`, `Sports & Racing`). 프론트가 번역하거나 코드로 해석하지 않는다. 묶음 이름이 바뀐 옛 공유 결과에서는 영어 대문자 키(`EXPLORATION` 등)가 그대로 올 수 있다
- 시간 값은 JSON에서 `704.0`으로 오지만 JS에서는 `704`로 읽힌다. 화면에서 소수 첫째 자리를 보이려면 `toFixed(1)`을 쓴다

필드 규칙

- 비율(`ratio`, `zeroPlayRatio`)은 0~1 소수 셋째 자리, 시간(`hours`, `totalHours`)은 소수 첫째 자리다. 화면에서는 비율을 백분율로 바꾼다
- `topTags`의 `ratio`는 전체 태그 기준이라 상위 10개의 합이 1보다 작다. 값을 그대로 쓰고 합이 100%가 되게 다시 계산하지 않는다
- `profile`은 본인 결과 화면에만 쓴다. 공유 응답(3.3)에는 없다. `personaName`, `avatarUrl`이 없으면 null이 아니라 빈 문자열이다. 아바타가 비면 기본 이미지를 쓴다. `steamId64`는 4번 단계 호출에만 쓰고 링크나 화면에 노출하지 않는다
- `gamerStats`는 항상 5개이고 순서는 `EXPLORATION`, `STRATEGY`, `COLLECTION`, `DIVERSITY`, `COOP`로 고정이다. `value`는 0~100 정수 또는 `null`(근거 데이터 없음)이다. `COLLECTION`은 항상 숫자다. `null`이면 "기록 부족"으로 보인다. 실력 점수가 아니라 플레이 기록 기반 성향 지표라는 안내를 화면에 둔다
- 비어 있을 수 있는 값: `stats.mostPlayed`, `diagnosis.favoriteTag`, `diagnosis.hiddenTaste`는 `null`일 수 있다. `diagnosis.concentratedGames`는 0~3개, `stats.topTags`는 0~10개이고 빈 배열일 수 있다
- 태그 이름(`topTags`, `favoriteTag`, `hiddenTaste`)은 Steam 영어 태그 이름이다
- 코드 값: `ledger.code`는 `STARTER`, `LEGENDARY_COLLECTOR`, `COLLECTOR`, `EXPLORER`, `PRACTICAL`, `BALANCED`이다. `verdict.crime.code`는 `PILE_OF_SHAME`, `ONE_GAME_DEVOTION`, `GENRE_LOYALIST`, `GENRE_NOMAD`, `INNOCENT`이고, `verdict.sentence.code`는 `SENTENCE_` 뒤에 죄목 코드를 붙인 값이다. 코드는 늘어날 수 있으므로 모르는 코드는 기본 표시로 처리한다

`limitedData`

- 값이 `true`면 "기록이 적어 판정이 가벼울 수 있어요" 같은 안내를 붙인다. 조건은 플레이 시간이 모두 0이거나, 우리 DB와 맞는 게임이 5개 미만인 경우다
- `true`여도 응답은 200이고 모든 필드가 위 규칙대로 채워진다. 화면을 숨기지 않고 안내만 더한다
- 플레이 시간이 모두 0인 원인이 Steam의 "총 플레이 시간 비공개" 설정일 수 있다. 서버 코드로 확인하지 못한 추정이므로 안내 문구에 "플레이 시간 공개 여부도 확인"을 넣을지는 화면 담당이 정한다

### 3.2 POST /api/library/share

요청 `{ "analysisId": "8d4c1f0e-...." }` → 응답 200 `{ "shareId": "ZlrO0AqTzWoflNwdQYeXiw" }`

- `shareId`는 22자이고 문자는 `A-Z`, `a-z`, `0-9`, `-`, `_`다. 주소에 그대로 쓸 수 있다
- `analysisId`는 비어 있거나 64자를 넘으면 400 `VALIDATION_ERROR`다
- `analysisId`는 서버 메모리에 분석을 만든 시점부터 30분 동안 있다. 조회해도 늘어나지 않는다. 30분이 지나거나, 서버 재시작과 배포로 사라지면 404 `ANALYSIS_EXPIRED`다
- 프론트는 입력한 Steam ID를 화면 상태(메모리)에 들고 있다. 404 `ANALYSIS_EXPIRED`를 받으면 그 Steam ID로 분석을 다시 호출하고, 새 `analysisId`로 공유를 한 번 더 보낸다
- 같은 `analysisId`로 공유를 반복하면 30분 안에는 같은 `shareId`가 온다. 다시 분석하면 새 `shareId`가 나온다. 받은 `shareId`는 화면 상태에 들고 있어 버튼을 다시 눌러도 재호출하지 않는다
- 공유 결과는 90일 동안 보관된다

### 3.3 GET /api/library/share/{shareId}?lang=ko

3.1 응답에서 `analysisId`와 `profile`을 뺀 모양이다. 없거나 만료되었거나 더 이상 읽을 수 없으면, 또는 `shareId` 형식이 틀려도 404 `SHARE_NOT_FOUND`다(400이 아니다). 공유 결과는 언어와 상관없이 저장되므로, 같은 링크를 `lang=ko`로 열면 한국어, `lang=en`으로 열면 영어 문구가 나온다. 이 경로는 요청 횟수 제한이 없다.

## 4. 오류

공통 오류 모양(`{errorCode, message, timestamp, details}`)과 429 처리는 `api-contract-taste.md`와 같다. 우리 오류 코드는 `message`에 온다. 서버 처리기가 만든 응답은 `Cache-Control: no-cache, no-store`라 캐시되지 않는다. 같은 상태 코드가 여러 개 있으므로(404가 세 가지) 호출한 API와 `message`(`tasteRequest`의 `err.code`)로 구분한다.

| 상태 | errorCode | message | 화면 안내 |
|---|---|---|---|
| 400 | `BAD_REQUEST` | `INVALID_STEAM_ID` | Steam ID 형식을 확인하라고 안내한다 |
| 400 | `VALIDATION_ERROR` | `Invalid request` | `steamId`가 비었거나 200자를 넘음, `clientId` 형식 오류(빈 문자열 포함), `analysisId` 누락이나 64자 초과, JSON 형식 오류, 문자열이 아닌 타입. 입력을 확인하라는 일반 안내로 처리한다 |
| 404 | `NOT_FOUND` | `STEAM_PROFILE_NOT_FOUND` | 그런 프로필을 찾지 못했다고 안내한다. 숫자 ID 경로의 이 결과는 서버가 2분 동안 기억한다 |
| 403 | `FORBIDDEN` | `LIBRARY_PRIVATE` | 프로필이나 게임 세부 정보가 비공개라고 알리고 공개로 바꾸는 방법을 안내한다. 바꾼 뒤 2분쯤 지나 다시 시도하게 한다(서버가 비공개 결과를 2분 동안 기억한다) |
| 422 | `UNPROCESSABLE_ENTITY` | `LIBRARY_EMPTY` | 보유한 게임이 없다고 안내한다. 이 결과도 서버가 2분 동안 기억한다 |
| 503 | `SERVICE_UNAVAILABLE` | 영어 일반 문장(아래 설명) | Steam이 응답하지 않거나 잠시 붐빈다고 안내하고, 잠시 뒤 한 번 다시 시도하게 한다 |
| 500 | `INTERNAL_SERVER_ERROR` | 영어 일반 문장(아래 설명) | 일시적인 문제라고 안내하고 잠시 뒤 다시 시도하게 한다 |
| 404 | `NOT_FOUND` | `ANALYSIS_EXPIRED` | 사용자에게 다시 분석하라고 안내한다. 자동으로 재분석하는 방법은 3.2에 있다 |
| 404 | `NOT_FOUND` | `SHARE_NOT_FOUND` | 공유가 만료되었거나 없다고 안내한다 |
| 429 | 없음 | 없음 | 아래 설명 |

- 503과 500의 `message`는 같은 영어 문장 `There was a temporary problem with the system, please try again in a moment.`이다. 서버 내부 사유가 밖으로 나가지 않게 일반 문장으로 바꾼 것이다. 상태 코드로 분기하고 이 문장은 화면에 그대로 보이지 않는다. `Retry-After`는 오지 않는다
- 503이 나는 경우: Steam 응답 시간 초과, Steam 인증 문제, Steam 호출 동시 8개 한도 초과, 사용자 지정 이름 조회 실패, Steam 응답 형식 이상. 서버가 503은 기억하지 않으므로 바로 재시도할 수 있다
- 성공한 Steam 결과는 서버가 10분 캐시한다. 분석 직후 다시 분석해도 방금 한 플레이 시간이 늘어나 보이지 않을 수 있다
- 429는 분석이 IP당 분당 10번, 공유 저장이 분당 20번이다. 본문은 `{ "error": "rate_limited", "retryAfterSeconds": N }`이다. 분석이나 공유 저장이 429이면 `retryAfterSeconds`초 뒤에 다시 누르라고 안내하고 그동안 버튼을 잠근다. 이벤트 429는 조용히 버린다. 같은 IP를 여럿이 쓰는 곳에서는 분석이 429에 걸릴 수 있다. 429 응답에는 `Cache-Control`을 확인하지 않았다
- `POST`에 `Content-Type: application/json`이 없으면 서버가 500으로 돌려준다(taste 계약 1절과 같다)

## 5. 이벤트

`POST /api/events`는 `api-contract-taste.md` 3.6의 규칙을 따르고, 그 표의 `ref` 허용 값에 `LIBRARY`가 더해진다. 이 화면에서 쓰는 조합은 다음과 같다.

| type | ref | channel |
|---|---|---|
| FEATURE_SELECT | `LIBRARY` | 없음 |
| SHARE_CLICK | 유형 코드 또는 `LIBRARY` | `LINK`, `IMAGE` 필수 |
| SHARE_INFLOW | 유형 코드 또는 `LIBRARY` | 없음 |
| LIBRARY_COMPLETE | 보내지 않는다 | 보내지 않는다 |

- `SHARE_INFLOW`에 `channel`을 넣으면 400 `INVALID_EVENT`다
- `LIBRARY_COMPLETE`를 프론트가 보내면 400 `INVALID_EVENT`다
- `ref`의 `LIBRARY`는 대소문자를 가리지 않고 서버가 대문자로 저장한다
- 응답은 202이고 본문이 없다. `fireAndForget`으로 보낸다
