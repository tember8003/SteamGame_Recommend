package SteamGame.recommend.domain.library;

import SteamGame.recommend.domain.library.analysis.LibraryAnalysis;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

// 공유로 DB에 저장되는 JSON의 바깥 모양, 쓰기와 읽기가 이 레코드 하나를 거친다
// 닉네임, 아바타, SteamID64는 담지 않는다, 언어는 읽을 때 고른다
// v는 저장 형식 번호이고 analysis의 모양이 바뀌면 올린다
@JsonIgnoreProperties(ignoreUnknown = true)
public record SharedSnapshot(
        @JsonProperty("v") int v,
        @JsonProperty("analysis") LibraryAnalysis analysis) {

    public static final int CURRENT_VERSION = 1;

    public SharedSnapshot {
        Objects.requireNonNull(analysis, "analysis");
    }
}
