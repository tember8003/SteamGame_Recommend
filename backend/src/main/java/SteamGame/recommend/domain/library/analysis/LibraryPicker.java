package SteamGame.recommend.domain.library.analysis;

import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.AbandonedGenre;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.Picks;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.PlayNext;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.Revisit;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.TagShare;
import SteamGame.recommend.domain.library.content.LibraryContent;
import SteamGame.recommend.domain.library.content.LibraryContentValidator;
import SteamGame.recommend.domain.library.content.PickRules;
import SteamGame.recommend.domain.library.steam.OwnedGame;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 라이브러리 안에서 게임을 고르는 계산, 순수 함수다 (DB, 난수 없음, 시각은 인자로 받음)
// 지금 해볼 게임: 플레이 0분인 게임 중 사용자가 많이 한 태그와 겹치고 평가가 좋은 게임
// 다시 켤 게임: 오래 했지만 한동안 안 켠 게임
// 안 맞는 장르: 잠깐 하고 그만둔 게임이 가장 많이 몰린 장르
public final class LibraryPicker {

    private LibraryPicker() {
    }

    // tasteTags는 분석이 계산한 사용자의 태그별 플레이 시간 비율(내림차순), ratings에 없는 게임은 평가를 몰라 추천하지 않는다
    public static Picks pick(List<OwnedGame> games, Map<Long, Map<String, Integer>> tagVotesByAppid,
                             Map<Long, ReviewRating> ratings, List<TagShare> tasteTags,
                             LibraryContent content, Instant now) {
        PickRules rules = content.picks();
        return new Picks(
                playNext(games, tagVotesByAppid, ratings, tasteTags, content, rules),
                revisit(games, rules, now),
                abandonedGenre(games, tagVotesByAppid, content, rules, now));
    }

    private static List<PlayNext> playNext(List<OwnedGame> games, Map<Long, Map<String, Integer>> tagVotesByAppid,
                                           Map<Long, ReviewRating> ratings, List<TagShare> tasteTags,
                                           LibraryContent content, PickRules rules) {
        Map<String, Double> taste = new LinkedHashMap<>();
        for (TagShare t : tasteTags.subList(0, Math.min(rules.tasteTagCount(), tasteTags.size()))) {
            taste.put(t.tag(), t.ratio());
        }
        if (taste.isEmpty()) {
            return List.of();
        }
        Set<String> excluded = new HashSet<>(content.excludedTags());
        List<Candidate> candidates = new ArrayList<>();
        for (OwnedGame g : games) {
            Map<String, Integer> votes = tagVotesByAppid.get(g.appid());
            ReviewRating rating = ratings.get(g.appid());
            if (g.minutesTotal() != 0 || votes == null || rating == null
                    || rating.totalReviews() < rules.minReviews() || rating.positivePct() < rules.minPositivePct()) {
                continue;
            }
            // 흔한 태그를 빼고 득표수 순으로 그 게임의 대표 태그를 고른다, 같으면 이름 순서로 고정한다
            List<String> top = votes.entrySet().stream()
                    .filter(e -> !excluded.contains(e.getKey()) && e.getValue() != null)
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .limit(rules.candidateTagCount())
                    .map(Map.Entry::getKey)
                    .toList();
            // 사용자가 많이 한 태그 순서로 겹친 태그를 담는다
            List<String> matched = taste.keySet().stream().filter(top::contains).toList();
            if (matched.size() < rules.minMatchedTags()) {
                continue;
            }
            double score = matched.stream().mapToDouble(taste::get).sum();
            candidates.add(new Candidate(g, matched, score, rating));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed()
                .thenComparing(Comparator.comparingDouble((Candidate c) -> c.rating().positivePct()).reversed())
                .thenComparing(Comparator.comparingInt((Candidate c) -> c.rating().totalReviews()).reversed())
                .thenComparingLong(c -> c.game().appid()));
        return candidates.stream().limit(rules.maxPlayNext())
                .map(c -> new PlayNext(c.game().appid(), displayName(c.game()), c.matched()))
                .toList();
    }

    // 마지막 플레이 시각을 모르는 게임(Steam이 0을 준 경우)은 판단하지 않는다
    private static List<Revisit> revisit(List<OwnedGame> games, PickRules rules, Instant now) {
        double minMinutes = rules.revisitMinHours() * 60;
        return games.stream()
                .filter(g -> g.minutesTotal() >= minMinutes && g.lastPlayed() != null
                        && idleDays(g, now) >= rules.revisitIdleDays())
                .sorted(Comparator.comparingInt(OwnedGame::minutesTotal).reversed()
                        .thenComparingLong(OwnedGame::appid))
                .limit(rules.maxRevisit())
                .map(g -> new Revisit(g.appid(), displayName(g), hours(g.minutesTotal()), (int) idleDays(g, now)))
                .toList();
    }

    // 장르가 같으면 장르 순서가 앞선 쪽을 고른다
    private static AbandonedGenre abandonedGenre(List<OwnedGame> games, Map<Long, Map<String, Integer>> tagVotesByAppid,
                                                 LibraryContent content, PickRules rules, Instant now) {
        Map<String, String> genreOfTag = GenreProfiler.genreOfTag(content);
        Map<String, List<OwnedGame>> byGenre = new HashMap<>();
        for (OwnedGame g : games) {
            if (g.minutesTotal() < rules.abandonedMinMinutes() || g.minutesTotal() > rules.abandonedMaxMinutes()
                    || g.lastPlayed() == null || idleDays(g, now) < rules.abandonedIdleDays()) {
                continue;
            }
            String genre = GenreProfiler.representativeGenre(tagVotesByAppid.get(g.appid()), genreOfTag,
                    content.genreConditionalExcludes());
            if (genre != null) {
                byGenre.computeIfAbsent(genre, k -> new ArrayList<>()).add(g);
            }
        }
        String chosen = null;
        int chosenCount = 0;
        for (String genre : LibraryContentValidator.GENRE_KEYS) {
            int count = byGenre.getOrDefault(genre, List.of()).size();
            if (count >= rules.abandonedMinGames() && count > chosenCount) {
                chosen = genre;
                chosenCount = count;
            }
        }
        if (chosen == null) {
            return null;
        }
        List<String> examples = byGenre.get(chosen).stream()
                .sorted(Comparator.comparingInt(OwnedGame::minutesTotal).reversed().thenComparingLong(OwnedGame::appid))
                .limit(3)
                .map(LibraryPicker::displayName)
                .toList();
        return new AbandonedGenre(chosen, chosenCount, examples);
    }

    private static long idleDays(OwnedGame g, Instant now) {
        return Math.max(0, Duration.between(g.lastPlayed(), now).toDays());
    }

    // 이름이 비어 있으면 앱 번호로 표시한다(분석기와 같은 규칙)
    private static String displayName(OwnedGame g) {
        return g.name() == null || g.name().isBlank() ? "App " + g.appid() : g.name();
    }

    private static double hours(int minutes) {
        return Math.round(minutes / 60.0 * 10) / 10.0;
    }

    private record Candidate(OwnedGame game, List<String> matched, double score, ReviewRating rating) {
    }
}
