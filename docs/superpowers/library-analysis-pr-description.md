## Summary
- 2D 리뉴얼 2차인 Steam 라이브러리 분석 백엔드를 추가한다. Steam ID(17자리 숫자, 프로필 주소, 사용자 지정 주소)를 받아 라이브러리를 읽고, 장부, 판결문, 성향 지표 5개, 취향 진단서를 계산해 돌려준다.
- 결과는 사용자가 공유 버튼을 눌렀을 때만 Steam ID, 닉네임, 아바타 없이 저장하고, 90일 동안 공유 링크로 다시 볼 수 있다.
- 새 경로 `/api/library/**`만 추가한다. 기존 API와 기존 `SteamApiService`는 바꾸지 않는다.

## Flow
분석 요청(`POST /api/library/analyze`)은 다음 순서로 돈다.

- `SteamIdResolver`가 입력을 SteamID64로 바꾼다. 사용자 지정 주소는 Steam `ResolveVanityURL`로 바꾸고, 형식이 틀리면 400, 없는 이름은 404, 조회 장애는 503이다.
- `SteamLibraryClient`가 `GetPlayerSummaries`로 공개 여부를 먼저 보고, 공개일 때만 `GetOwnedGames`를 부른다. 결과는 공개(게임 목록), 비공개, 없음, 게임 0개, 장애 중 하나이고 예외를 밖으로 던지지 않는다. Steam이 빈 응답이나 이상한 응답을 주면 사용자 오류가 아니라 장애로 본다.
- 공개 결과는 10분, 비공개와 없음과 게임 0개는 2분 동안 기억한다. 동시에 Steam을 부르는 요청은 8개까지이고, 자리를 얻지 못하면 503이다.
- 보유 게임의 태그를 1,000개씩 나눠 DB에서 읽는다.
- `LibraryAnalyzer`(순수 함수)가 판정을 계산한다. 판정은 위에서부터 처음 맞는 하나만 고르고, 문구 후보는 SteamID64 해시로 골라 같은 사람에게 같은 문구가 나온다.
- 분석 결과는 30분 동안 메모리에 두고 `analysisId`로 찾는다. 응답의 `profile`(닉네임, 아바타, `steamId64`)은 본인 화면에만 나간다.

공유(`POST /api/library/share`)는 분석 결과를 `{"v":1,"analysis":...}` 형식으로 `library_share`에 저장하고 22자 `shareId`를 돌려준다. 같은 분석을 다시 공유하면 같은 `shareId`다. 공유 조회(`GET /api/library/share/{shareId}?lang=`)는 저장된 코드와 숫자로 요청 언어의 문구를 다시 그린다. 읽을 수 없는 스냅샷은 ERROR 로그와 함께 404이고, 문구 결함은 500으로 드러난다. 만료된 공유는 매일 04:30에 지운다.

## Changes
- `domain/library/steam/` — 입력 해석(`SteamIdResolver`), Steam 응답 해석(`SteamLibraryParser`), Steam 호출과 캐시와 동시 호출 제한(`SteamLibraryClient`), 결과 타입(`SteamLibraryResult`)
- `src/main/resources/library/library-v1.json`, `domain/library/content/` — 판정 문구(한국어, 영어, 판정마다 후보 2~3개), 태그 묶음 12개와 묶음 이름, 제외한 흔한 태그, 기동 시 검증
- `domain/library/analysis/` — 장부, 죄목과 판결, 성향 지표, 진단서 계산
- `domain/library/share/` — 공유 저장, 조회, 만료 정리
- `domain/library/LibraryAnalysisService`, `SharedSnapshot`, `dto/`, `controller/LibraryController` — 분석과 공유 API, 응답 변환
- `db/migration/V9__library_share_and_event.sql` — `library_share` 테이블, `funnel_event`에 `LIBRARY_COMPLETE` 허용
- `domain/event/` — 공유 이벤트의 `ref`에 `LIBRARY` 허용, `LIBRARY_COMPLETE`는 서버만 남김
- `domain/taste/service/TagRarityCache` — 희소도를 읽었는지 알려 주는 `isLoaded`
- `infra/web/RateLimitFilter`, `application.properties` — 분석 분당 10번, 공유 저장 20번(IP 기준), Steam 시간 제한, 캐시, 동시 호출 설정

## Test
- `SteamIdResolverTest`, `SteamLibraryParserTest`, `SteamLibraryClientTest` — 입력 형식, 비공개와 장애 구분, 게임 4,000개 응답, 시간 초과, 캐시와 만료, 2분 기억, 동시 호출 제한, 로그에 닉네임과 게임 이름 없음
- `LibraryContentValidatorTest`, `LibraryContentFileTest` — 문구와 자리표시자 일치, 태그가 운영 태그 목록에 있음, 흔한 태그 제외
- `LibraryAnalyzerTest` — 판정 경계값 양쪽, 판정 조합 전수 확인, 지표와 다양성 계산, 숨은 취향. 판정 경계 여섯 곳의 돌연변이 검사에서 모두 실패를 확인했다
- `LibraryShareRepositoryTest`, `LibraryShareServiceTest`, `TagRepositoryGameTagMappingTest` — 저장, 만료, 실제 태그 조회 쿼리
- `LibraryAnalysisServiceTest`, `LibraryViewMapperTest`, `LibraryControllerTest`, `RateLimitLibraryTest` — 오류 매핑, 공유 스냅샷 견본 파일, 운영 설정으로 저장과 조회 왕복, 스냅샷에 개인정보 없음, null 키 유지, 요청 제한
- 전체 백엔드 테스트 691건 통과(1차 머지 시점 478건)

## 롤백
앱만 이전 버전으로 되돌리면 된다. V9는 새 테이블을 더하고 이벤트 제약을 넓힐 뿐이고, 이전 앱은 `funnel_event`를 읽지 않으므로 `LIBRARY_COMPLETE` 기록이 남아도 깨지지 않는다.

## 후속
- 분석 결과 캐시는 서버 메모리라 서버를 여러 대로 늘리면 공유 저장이 다른 서버로 가서 `ANALYSIS_EXPIRED`가 날 수 있다. 지금은 서버가 한 대라 그대로 둔다
- Steam 503이 날 때마다 기존 전역 오류 처리기가 스택을 ERROR로 남겨 로그가 많아진다. 기존 처리기 정책이라 따로 고친다
- `RateLimitFilter`는 디코딩 전 경로를 그대로 비교한다. 기존 경로 전체에 해당하는 구조라 따로 고친다
- 판정 임계값과 태그 묶음 배치는 실제 사용자 데이터로 판정 분포를 본 뒤 조정한다

close #N
