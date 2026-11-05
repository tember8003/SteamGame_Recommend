package SteamGame.recommend.domain.library.analysis;

import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.GenreProfile;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.GenreShare;
import SteamGame.recommend.domain.library.content.GenreConditionalExclude;
import SteamGame.recommend.domain.library.content.GenreRules;
import SteamGame.recommend.domain.library.content.LibraryContent;
import SteamGame.recommend.domain.library.content.LibraryContentValidator;
import SteamGame.recommend.domain.library.steam.OwnedGame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 거점 배경을 정하는 장르 계산, 순수 함수다 (DB, 시간, 난수 없음)
// 게임마다 장르에 속한 태그 중 득표수가 가장 높은 태그의 장르 하나에 플레이 시간을 모두 넣는다
// 한 게임의 시간을 여러 장르에 나누지 않는다, 장르 득표수가 같으면 GENRE_KEYS 순서가 앞선 장르를 고른다
public final class GenreProfiler {

    private static final double MINUTES_PER_HOUR = 60.0;
    // 기준값과 정확히 같은 비율이 부동소수 오차로 반대쪽에 떨어지지 않게 둔다, 기준값과 같으면 기준을 넘은 것으로 본다
    private static final double EPS = 1e-9;

    private GenreProfiler() {
    }

    // tagVotesByAppid는 appid별 태그 이름과 득표수, 카탈로그에 없는 게임은 키가 없다
    public static GenreProfile profile(List<OwnedGame> games, Map<Long, Map<String, Integer>> tagVotesByAppid,
                                       LibraryContent content) {
        Map<String, String> genreOfTag = genreOfTag(content);

        Map<String, Long> minutesByGenre = new LinkedHashMap<>();
        for (String genre : LibraryContentValidator.GENRE_KEYS) {
            minutesByGenre.put(genre, 0L);
        }
        long totalMinutes = 0;
        long classifiedMinutes = 0;
        for (OwnedGame g : games) {
            if (g.minutesTotal() <= 0) {
                continue;
            }
            totalMinutes += g.minutesTotal();
            String genre = representativeGenre(tagVotesByAppid.get(g.appid()), genreOfTag,
                    content.genreConditionalExcludes());
            if (genre != null) {
                minutesByGenre.merge(genre, (long) g.minutesTotal(), Long::sum);
                classifiedMinutes += g.minutesTotal();
            }
        }

        // 시간 내림차순, 같으면 GENRE_KEYS 순서로 고정한다 (LinkedHashMap 순서와 안정 정렬을 이용함)
        List<Map.Entry<String, Long>> ranked = new ArrayList<>(minutesByGenre.entrySet());
        ranked.removeIf(e -> e.getValue() <= 0);
        ranked.sort(Map.Entry.<String, Long>comparingByValue().reversed());

        List<GenreShare> distribution = new ArrayList<>();
        for (Map.Entry<String, Long> e : ranked) {
            distribution.add(new GenreShare(e.getKey(), ratio((double) e.getValue() / classifiedMinutes)));
        }
        double coverageRaw = totalMinutes == 0 ? 0 : (double) classifiedMinutes / totalMinutes;

        GenreRules rules = content.genreRules();
        // 분류된 시간이 없으면 기준값이 0이어도 1위 장르가 없으므로 기록 부족으로 본다
        if (classifiedMinutes == 0
                || classifiedMinutes / MINUTES_PER_HOUR < rules.minClassifiedHours() - EPS
                || coverageRaw < rules.minCoverageRatio() - EPS) {
            return new GenreProfile(null, null, GenreState.INSUFFICIENT, ratio(coverageRaw), distribution);
        }
        // 비교는 반올림 전 비율로 한다
        double firstRaw = (double) ranked.get(0).getValue() / classifiedMinutes;
        double secondRaw = ranked.size() < 2 ? 0 : (double) ranked.get(1).getValue() / classifiedMinutes;
        String secondary = ranked.size() >= 2 && secondRaw >= rules.secondaryMinRatio() - EPS ? ranked.get(1).getKey() : null;
        GenreState state = firstRaw - secondRaw < rules.mixedGapRatio() - EPS ? GenreState.MIXED : GenreState.READY;
        return new GenreProfile(ranked.get(0).getKey(), secondary, state, ratio(coverageRaw), distribution);
    }

    static Map<String, String> genreOfTag(LibraryContent content) {
        Map<String, String> genreOfTag = new HashMap<>();
        content.genres().forEach((genre, tags) -> tags.forEach(tag -> genreOfTag.put(tag, genre)));
        return genreOfTag;
    }

    // 장르에 속한 태그가 하나도 없으면 null, 장르마다 그 장르 태그 중 가장 높은 득표수를 점수로 삼는다
    // 조건 태그가 있는 게임은 그 규칙의 ignoreTags 표를 세지 않는다
    static String representativeGenre(Map<String, Integer> tagVotes, Map<String, String> genreOfTag,
                                              List<GenreConditionalExclude> conditionalExcludes) {
        if (tagVotes == null) {
            return null;
        }
        Set<String> ignored = new HashSet<>();
        for (GenreConditionalExclude rule : conditionalExcludes) {
            if (rule.whenAnyTag().stream().anyMatch(tagVotes::containsKey)) {
                ignored.addAll(rule.ignoreTags());
            }
        }
        Map<String, Integer> best = new HashMap<>();
        tagVotes.forEach((tag, votes) -> {
            String genre = genreOfTag.get(tag);
            if (genre != null && votes != null && !ignored.contains(tag)) {
                best.merge(genre, votes, Math::max);
            }
        });
        String chosen = null;
        int chosenVotes = Integer.MIN_VALUE;
        for (String genre : LibraryContentValidator.GENRE_KEYS) {
            Integer v = best.get(genre);
            // 더 클 때만 바꿔서 동점이면 앞 순서 장르가 남는다
            if (v != null && v > chosenVotes) {
                chosen = genre;
                chosenVotes = v;
            }
        }
        return chosen;
    }

    private static double ratio(double r) {
        return Math.round(r * 1000) / 1000.0;
    }
}
