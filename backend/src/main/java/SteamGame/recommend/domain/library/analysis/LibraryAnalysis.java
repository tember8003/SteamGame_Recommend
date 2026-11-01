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
// 자바 필드 이름을 바꿔도 저장 형식이 달라지지 않으며, 저장 형식을 바꾸면 스냅샷 버전을 올려야 한다
// 읽을 때 필수 값이 빠지면 실패한다, 모르는 필드는 무시한다
@JsonIgnoreProperties(ignoreUnknown = true)
public record LibraryAnalysis(
        @JsonProperty("limitedData") boolean limitedData,
        @JsonProperty("stats") Stats stats,
        @JsonProperty("ledger") Judged ledger,
        @JsonProperty("verdict") Verdict verdict,
        @JsonProperty("gamerStats") List<GamerStat> gamerStats,
        @JsonProperty("diagnosis") Diagnosis diagnosis) {

    public LibraryAnalysis {
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(diagnosis, "diagnosis");
        gamerStats = List.copyOf(gamerStats);
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
