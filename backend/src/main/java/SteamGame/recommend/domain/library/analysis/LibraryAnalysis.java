package SteamGame.recommend.domain.library.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// 분석 결과 전체, 문구 원문은 담지 않고 코드와 후보 번호와 채울 값만 담는다
// 이 레코드들은 공유 스냅샷으로 저장되므로 JSON 이름은 @JsonProperty로 고정한다
// 자바 필드 이름을 바꿔도 저장 형식이 달라지지 않는다
// 필드를 지우거나 뜻을 바꾸면 스냅샷 버전을 올려야 한다, 선택 필드를 더할 때는 읽는 쪽이 옛 저장분에 null을 채우면 버전을 유지한다(genreProfile)
// 읽을 때 필수 값이 빠지면 실패한다, 모르는 필드는 무시한다
@JsonIgnoreProperties(ignoreUnknown = true)
public record LibraryAnalysis(
        @JsonProperty("limitedData") boolean limitedData,
        @JsonProperty("stats") Stats stats,
        @JsonProperty("ledger") Judged ledger,
        @JsonProperty("verdict") Verdict verdict,
        @JsonProperty("gamerStats") List<GamerStat> gamerStats,
        @JsonProperty("diagnosis") Diagnosis diagnosis,
        @JsonProperty("genreProfile") GenreProfile genreProfile,
        @JsonProperty("picks") Picks picks) {

    // genreProfile과 picks는 그 기능 전에 저장된 공유에 없으므로 null을 허용한다
    public LibraryAnalysis {
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(diagnosis, "diagnosis");
        gamerStats = List.copyOf(gamerStats);
    }

    public LibraryAnalysis withGenreProfile(GenreProfile profile) {
        return new LibraryAnalysis(limitedData, stats, ledger, verdict, gamerStats, diagnosis, profile, picks);
    }

    public LibraryAnalysis withPicks(Picks value) {
        return new LibraryAnalysis(limitedData, stats, ledger, verdict, gamerStats, diagnosis, genreProfile, value);
    }

    // 시간은 소수 첫째 자리, 비율은 소수 셋째 자리, mostPlayed가 null이면 플레이 시간이 없는 것
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stats(
            @JsonProperty("ownedCount") int ownedCount,
            @JsonProperty("totalHours") double totalHours,
            @JsonProperty("zeroPlayCount") int zeroPlayCount,
            @JsonProperty("zeroPlayRatio") double zeroPlayRatio,
            @JsonProperty("mostPlayed") TopGame mostPlayed,
            @JsonProperty("recentGameCount") int recentGameCount,
            @JsonProperty("recentHours") double recentHours,
            @JsonProperty("matchedCount") int matchedCount,
            @JsonProperty("topTags") List<TagShare> topTags) {

        public Stats {
            topTags = List.copyOf(topTags);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopGame(
            @JsonProperty("appid") long appid,
            @JsonProperty("name") String name,
            @JsonProperty("hours") double hours,
            @JsonProperty("ratio") double ratio) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TagShare(
            @JsonProperty("tag") String tag,
            @JsonProperty("hours") double hours,
            @JsonProperty("ratio") double ratio) {
    }

    // code는 문구 코드, phraseIndex번째 후보를 쓰고 params는 그 문구가 쓰는 자리표시자 값만 담는다
    // params 값의 자바 타입은 저장 뒤 달라질 수 있다(Long이 Integer로 돌아오는 식), 값은 문자열과 숫자만 쓴다
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Judged(
            @JsonProperty("code") String code,
            @JsonProperty("phraseIndex") int phraseIndex,
            @JsonProperty("params") Map<String, Object> params) {

        // 응답에 나가는 순서가 바뀌지 않도록 넣은 순서를 지키며 복사한다
        public Judged {
            Objects.requireNonNull(code, "code");
            params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Verdict(
            @JsonProperty("crime") Judged crime,
            @JsonProperty("sentence") Judged sentence) {

        public Verdict {
            Objects.requireNonNull(crime, "crime");
            Objects.requireNonNull(sentence, "sentence");
        }
    }

    // value가 null이면 근거 없음, code는 StatCode 상수 이름으로 저장된다
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GamerStat(
            @JsonProperty("code") StatCode code,
            @JsonProperty("value") Integer value) {

        public GamerStat {
            Objects.requireNonNull(code, "code");
        }
    }

    // 거점 배경을 정하는 장르 결과, 게임마다 대표 장르 하나에 플레이 시간을 모두 넣어 계산한다
    // primaryGenre는 state가 INSUFFICIENT면 null이고 그 밖에는 항상 있다
    // secondaryGenre는 2위 비율이 기준 미만이면 null이다, MIXED여도 여러 장르에 고르게 퍼져 2위가 기준 미만이면 null일 수 있다
    // state는 반올림 전 비율로 정하므로 반올림한 coverageRatio가 기준값과 같아 보여도 INSUFFICIENT일 수 있다
    // distribution은 분류된 시간 안에서의 비율이고 0보다 큰 장르만 비율 내림차순으로 담는다
    // coverageRatio는 전체 플레이 시간 중 장르로 분류된 시간의 비율이다
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenreProfile(
            @JsonProperty("primaryGenre") String primaryGenre,
            @JsonProperty("secondaryGenre") String secondaryGenre,
            @JsonProperty("state") GenreState state,
            @JsonProperty("coverageRatio") double coverageRatio,
            @JsonProperty("distribution") List<GenreShare> distribution) {

        // 손상된 공유 스냅샷이 화면까지 가지 않도록 주력과 보조 장르의 관계를 생성할 때 확인한다
        public GenreProfile {
            Objects.requireNonNull(state, "state");
            if ((state == GenreState.INSUFFICIENT) != (primaryGenre == null)) {
                throw new IllegalArgumentException("primaryGenre must be null only when INSUFFICIENT");
            }
            if (secondaryGenre != null && primaryGenre == null) {
                throw new IllegalArgumentException("secondaryGenre needs primaryGenre");
            }
            distribution = List.copyOf(distribution);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenreShare(
            @JsonProperty("code") String code,
            @JsonProperty("ratio") double ratio) {

        public GenreShare {
            Objects.requireNonNull(code, "code");
        }
    }

    // 라이브러리에서 고른 게임들, 조건에 맞는 게임이 없으면 목록은 비고 abandonedGenre는 null이다
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Picks(
            @JsonProperty("playNext") List<PlayNext> playNext,
            @JsonProperty("revisit") List<Revisit> revisit,
            @JsonProperty("abandonedGenre") AbandonedGenre abandonedGenre) {

        public Picks {
            playNext = List.copyOf(playNext);
            revisit = List.copyOf(revisit);
        }
    }

    // 플레이 0분인 게임 중 취향에 맞는 게임, matchedTags는 사용자가 많이 한 태그 순서로 담는다
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlayNext(
            @JsonProperty("appid") long appid,
            @JsonProperty("name") String name,
            @JsonProperty("matchedTags") List<String> matchedTags) {

        public PlayNext {
            Objects.requireNonNull(name, "name");
            matchedTags = List.copyOf(matchedTags);
        }
    }

    // 오래 했지만 한동안 안 켠 게임, idleDays는 분석 시점 기준 마지막 플레이부터 지난 날 수
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Revisit(
            @JsonProperty("appid") long appid,
            @JsonProperty("name") String name,
            @JsonProperty("hours") double hours,
            @JsonProperty("idleDays") int idleDays) {

        public Revisit {
            Objects.requireNonNull(name, "name");
        }
    }

    // 잠깐 하고 그만둔 게임이 가장 많이 몰린 장르, games는 예시 이름 최대 3개
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AbandonedGenre(
            @JsonProperty("genre") String genre,
            @JsonProperty("gameCount") int gameCount,
            @JsonProperty("games") List<String> games) {

        public AbandonedGenre {
            Objects.requireNonNull(genre, "genre");
            games = List.copyOf(games);
        }
    }

    // favoriteTag와 hiddenTaste가 null이면 근거 없음
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Diagnosis(
            @JsonProperty("favoriteTag") String favoriteTag,
            @JsonProperty("concentratedGames") List<TopGame> concentratedGames,
            @JsonProperty("hiddenTaste") String hiddenTaste) {

        public Diagnosis {
            concentratedGames = List.copyOf(concentratedGames);
        }
    }
}
