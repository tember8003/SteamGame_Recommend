package SteamGame.recommend.domain.library.analysis;

import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.Diagnosis;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.GamerStat;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.Judged;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.Stats;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.TagShare;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.TopGame;
import SteamGame.recommend.domain.library.analysis.LibraryAnalysis.Verdict;
import SteamGame.recommend.domain.library.content.LibraryContent;
import SteamGame.recommend.domain.library.content.Phrase;
import SteamGame.recommend.domain.library.steam.OwnedGame;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

// 라이브러리 분석 계산, 순수 함수다 (DB, 시간, 난수 없음)
// 게임 목록이 비어 있으면 호출하지 않는 것이 전제이고 들어오면 IllegalArgumentException을 던진다 (호출 쪽이 먼저 422로 처리함)
// 판정 기준은 설계 문서 4.4를 따른다
public final class LibraryAnalyzer {

    private static final int LIMITED_MATCHED_MIN = 5;
    private static final double EPS = 1e-9;

    private LibraryAnalyzer() {
    }

    // rarity는 null이거나 null을 돌려줄 수 있고, 그러면 희소도로 숨은 취향을 고르는 경로를 건너뛴다
    public static LibraryAnalysis analyze(
            List<OwnedGame> games,
            Map<Long, List<String>> tagsByAppid,
            Function<String, Double> rarity,
            LibraryContent content,
            String steamId64) {
        if (games == null || games.isEmpty()) {
            throw new IllegalArgumentException("games must not be empty");
        }

        Set<String> excludedSet = new HashSet<>(content.excludedTags());
        int owned = games.size();
        long totalMinutes = 0;
        long recentMinutes = 0;
        int zeroCount = 0;
        int playedCount = 0;
        int shortPlayed = 0;
        int recentGames = 0;
        int matched = 0;
        OwnedGame top = null;
        for (OwnedGame g : games) {
            totalMinutes += g.minutesTotal();
            recentMinutes += g.minutesTwoWeeks();
            if (g.minutesTotal() <= 0) {
                zeroCount++;
            } else {
                playedCount++;
                if (g.minutesTotal() >= 60 && g.minutesTotal() <= 300) {
                    shortPlayed++;
                }
            }
            if (g.minutesTwoWeeks() > 0) {
                recentGames++;
            }
            // 제외 태그를 뺀 태그가 하나라도 있어야 매칭으로 센다
            List<String> own = tagsByAppid.get(g.appid());
            if (own != null && own.stream().anyMatch(t -> !excludedSet.contains(t))) {
                matched++;
            }
            if (top == null || g.minutesTotal() > top.minutesTotal()
                    || (g.minutesTotal() == top.minutesTotal() && g.appid() < top.appid())) {
                top = g;
            }
        }

        double zeroRatio = (double) zeroCount / owned;
        double shortRatio = playedCount == 0 ? 0 : (double) shortPlayed / playedCount;
        TopGame mostPlayed = totalMinutes == 0 ? null : toTopGame(top, totalMinutes);
        // 퍼센트 값은 반올림 전 비율로 계산한다
        double topRatioRaw = totalMinutes == 0 ? 0 : (double) top.minutesTotal() / totalMinutes;

        // 태그별 시간, 묶음별 시간
        Set<String> excluded = excludedSet;
        Map<String, Double> tagMinutes = tagMinutes(games, tagsByAppid, excluded, false);
        Map<String, Double> recentTagMinutes = tagMinutes(games, tagsByAppid, excluded, true);
        List<Map.Entry<String, Double>> rankedTags = rank(tagMinutes);
        double tagTotal = tagMinutes.values().stream().mapToDouble(Double::doubleValue).sum();

        List<TagShare> topTags = new ArrayList<>();
        for (Map.Entry<String, Double> e : rankedTags.subList(0, Math.min(10, rankedTags.size()))) {
            topTags.add(new TagShare(e.getKey(), hours(e.getValue()), ratio(e.getValue() / tagTotal)));
        }

        Map<String, Double> groupMinutes = groupMinutes(games, tagsByAppid, content);
        double groupTotal = groupMinutes.values().stream().mapToDouble(Double::doubleValue).sum();
        String topGroup = null;
        double topGroupShare = 0;
        if (groupTotal > 0) {
            for (Map.Entry<String, Double> e : rank(groupMinutes)) {
                topGroup = e.getKey();
                topGroupShare = e.getValue() / groupTotal;
                break;
            }
        }

        Integer diversity = groupTotal > 0 ? diversity(groupMinutes, groupTotal, content.tagGroups().size()) : null;

        // 판정
        LedgerCode ledgerCode = ledger(owned, zeroCount, playedCount, shortPlayed);
        CrimeCode crimeCode = crime(zeroCount, owned, top.minutesTotal(), totalMinutes, topGroupShare, diversity);

        // 문구에 채울 값 전체, 문구마다 필요한 것만 골라 담는다
        TagShare favorite = topTags.isEmpty() ? null : topTags.get(0);
        double favoriteRaw = rankedTags.isEmpty() ? 0 : rankedTags.get(0).getValue() / tagTotal;
        Map<String, Object> all = new HashMap<>();
        all.put("ownedCount", owned);
        all.put("zeroCount", zeroCount);
        all.put("zeroRatioPct", pct(zeroRatio));
        all.put("totalHours", hours(totalMinutes));
        // 값이 없을 때(플레이 시간 0, 묶음에 든 태그 없음)는 빈 문자열과 0으로 둔다
        all.put("topGame", mostPlayed == null ? "" : mostPlayed.name());
        all.put("topGameHours", mostPlayed == null ? 0.0 : mostPlayed.hours());
        all.put("topRatioPct", mostPlayed == null ? 0 : pct(topRatioRaw));
        all.put("topTag", favorite == null ? "" : favorite.tag());
        all.put("topTagRatioPct", favorite == null ? 0 : pct(favoriteRaw));
        all.put("topGroup", topGroup == null ? "" : topGroup);
        all.put("topGroupRatioPct", pct(topGroupShare));
        all.put("shortPlayedRatioPct", pct(shortRatio));

        Judged ledger = judged(ledgerCode.name(), steamId64, content, all);
        Verdict verdict = new Verdict(
                judged(crimeCode.name(), steamId64, content, all),
                judged("SENTENCE_" + crimeCode.name(), steamId64, content, all));

        // 성향 지표
        Integer exploration = null;
        Integer strategy = null;
        Integer coop = null;
        if (groupTotal > 0) {
            exploration = groupStat(groupMinutes, "EXPLORATION", groupTotal);
            strategy = groupStat(groupMinutes, "STRATEGY", groupTotal);
            coop = groupStat(groupMinutes, "COOP", groupTotal);
        }
        int collection = (int) Math.round(60 * zeroRatio + 40 * Math.min(1.0, owned / 500.0));
        List<GamerStat> gamerStats = List.of(
                new GamerStat(StatCode.EXPLORATION, exploration),
                new GamerStat(StatCode.STRATEGY, strategy),
                new GamerStat(StatCode.COLLECTION, collection),
                new GamerStat(StatCode.DIVERSITY, diversity),
                new GamerStat(StatCode.COOP, coop));

        // 진단서
        List<TopGame> concentrated = new ArrayList<>();
        if (totalMinutes > 0) {
            List<OwnedGame> sorted = new ArrayList<>(games);
            sorted.sort(Comparator.comparingInt(OwnedGame::minutesTotal).reversed()
                    .thenComparingLong(OwnedGame::appid));
            for (OwnedGame g : sorted.subList(0, Math.min(3, sorted.size()))) {
                if (g.minutesTotal() > 0) {
                    concentrated.add(toTopGame(g, totalMinutes));
                }
            }
        }
        String hidden = hiddenTaste(rankedTags, rank(recentTagMinutes), rarity);
        Diagnosis diagnosis = new Diagnosis(favorite == null ? null : favorite.tag(), concentrated, hidden);

        Stats stats = new Stats(owned, hours(totalMinutes), zeroCount, ratio(zeroRatio), mostPlayed,
                recentGames, hours(recentMinutes), matched, topTags);
        boolean limited = totalMinutes == 0 || matched < LIMITED_MATCHED_MIN;
        // 장르 결과는 태그 득표수가 필요해서 호출 쪽이 GenreProfiler로 따로 붙인다
        return new LibraryAnalysis(limited, stats, ledger, verdict, gamerStats, diagnosis, null);
    }

    // 비율은 정수로 비교해 부동소수 오차를 피한다
    private static LedgerCode ledger(int owned, int zero, int played, int shortPlayed) {
        if (owned < 10) {
            return LedgerCode.STARTER;
        }
        if (owned >= 300 && zero * 2 >= owned) {
            return LedgerCode.LEGENDARY_COLLECTOR;
        }
        if (zero * 100 >= 40 * owned) {
            return LedgerCode.COLLECTOR;
        }
        // 플레이한 게임 중 1시간 이상 5시간 이하 비율
        if (played > 0 && shortPlayed * 100 >= 40 * played) {
            return LedgerCode.EXPLORER;
        }
        if (zero * 100 < 15 * owned) {
            return LedgerCode.PRACTICAL;
        }
        return LedgerCode.BALANCED;
    }

    private static CrimeCode crime(int zero, int owned, long topMinutes, long totalMinutes,
                                   double topGroupShare, Integer diversity) {
        if (zero * 100 >= 40 * owned && zero >= 10) {
            return CrimeCode.PILE_OF_SHAME;
        }
        // 가장 오래 한 게임이 총 시간의 절반 이상이고 총 100시간(6000분) 이상
        if (topMinutes * 2 >= totalMinutes && totalMinutes >= 6000) {
            return CrimeCode.ONE_GAME_DEVOTION;
        }
        if (topGroupShare >= 0.6 - EPS) {
            return CrimeCode.GENRE_LOYALIST;
        }
        if (diversity != null && diversity >= 80) {
            return CrimeCode.GENRE_NOMAD;
        }
        return CrimeCode.INNOCENT;
    }

    private static Judged judged(String code, String steamId64, LibraryContent content, Map<String, Object> all) {
        List<Phrase> candidates = content.phrases().get(code);
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("library content has no phrase for " + code);
        }
        int index = Math.floorMod((steamId64 + ":" + code).hashCode(), candidates.size());
        Map<String, Object> params = new LinkedHashMap<>();
        for (String name : candidates.get(index).params()) {
            if (!all.containsKey(name)) {
                throw new IllegalStateException("unknown placeholder " + name + " in " + code);
            }
            params.put(name, all.get(name));
        }
        return new Judged(code, index, params);
    }

    // 이름이 비어 있으면 앱 번호로 표시한다
    private static String displayName(OwnedGame g) {
        return g.name() == null || g.name().isBlank() ? "App " + g.appid() : g.name();
    }

    private static TopGame toTopGame(OwnedGame g, long totalMinutes) {
        return new TopGame(g.appid(), displayName(g), hours(g.minutesTotal()), ratio((double) g.minutesTotal() / totalMinutes));
    }

    // 게임 시간을 (제외 태그를 뺀) 태그 수로 나눠 각 태그에 더한다, 시간이 0인 게임과 태그 없는 게임은 건너뜀
    private static Map<String, Double> tagMinutes(List<OwnedGame> games, Map<Long, List<String>> tagsByAppid,
                                                  Set<String> excluded, boolean recent) {
        Map<String, Double> result = new TreeMap<>();
        for (OwnedGame g : games) {
            int minutes = recent ? g.minutesTwoWeeks() : g.minutesTotal();
            List<String> tags = tagsByAppid.get(g.appid());
            if (minutes <= 0 || tags == null) {
                continue;
            }
            List<String> usable = tags.stream().filter(t -> !excluded.contains(t)).distinct().toList();
            if (usable.isEmpty()) {
                continue;
            }
            double share = (double) minutes / usable.size();
            for (String t : usable) {
                result.merge(t, share, Double::sum);
            }
        }
        return result;
    }

    // 게임 시간을 묶음에 든 태그 수로 나눠 그 태그가 속한 묶음에 더한다
    private static Map<String, Double> groupMinutes(List<OwnedGame> games, Map<Long, List<String>> tagsByAppid,
                                                    LibraryContent content) {
        Map<String, Set<String>> groupsOfTag = new HashMap<>();
        for (Map.Entry<String, List<String>> e : content.tagGroups().entrySet()) {
            for (String tag : e.getValue()) {
                groupsOfTag.computeIfAbsent(tag, k -> new HashSet<>()).add(e.getKey());
            }
        }
        Map<String, Double> result = new TreeMap<>();
        for (String key : content.tagGroups().keySet()) {
            result.put(key, 0.0);
        }
        for (OwnedGame g : games) {
            List<String> tags = tagsByAppid.get(g.appid());
            if (g.minutesTotal() <= 0 || tags == null) {
                continue;
            }
            List<String> grouped = tags.stream().filter(groupsOfTag::containsKey).distinct().toList();
            if (grouped.isEmpty()) {
                continue;
            }
            double share = (double) g.minutesTotal() / grouped.size();
            for (String t : grouped) {
                for (String group : groupsOfTag.get(t)) {
                    result.merge(group, share, Double::sum);
                }
            }
        }
        return result;
    }

    // 시간 내림차순, 같으면 이름 순서로 고정
    private static List<Map.Entry<String, Double>> rank(Map<String, Double> minutes) {
        List<Map.Entry<String, Double>> list = new ArrayList<>(minutes.entrySet());
        list.sort(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
        return list;
    }

    private static int diversity(Map<String, Double> groupMinutes, double total, int groupCount) {
        if (groupCount < 2) {
            return 0;
        }
        double h = 0;
        for (double m : groupMinutes.values()) {
            if (m > 0) {
                double p = m / total;
                h -= p * Math.log(p);
            }
        }
        return (int) Math.round(h / Math.log(groupCount) * 100);
    }

    private static int groupStat(Map<String, Double> groupMinutes, String key, double total) {
        double share = groupMinutes.getOrDefault(key, 0.0) / total;
        return (int) Math.min(100, Math.round(2 * share * 100));
    }

    private static String hiddenTaste(List<Map.Entry<String, Double>> overall,
                                      List<Map.Entry<String, Double>> recent,
                                      Function<String, Double> rarity) {
        if (overall.isEmpty()) {
            return null;
        }
        Set<String> overallTop5 = new HashSet<>();
        for (Map.Entry<String, Double> e : overall.subList(0, Math.min(5, overall.size()))) {
            overallTop5.add(e.getKey());
        }
        for (Map.Entry<String, Double> e : recent.subList(0, Math.min(5, recent.size()))) {
            if (!overallTop5.contains(e.getKey())) {
                return e.getKey();
            }
        }
        // 희소도를 알 수 없으면(함수가 없거나 값이 null) 임의 태그를 고르지 않고 비워 둔다
        if (rarity == null) {
            return null;
        }
        String best = null;
        double bestRarity = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, Double> e : overall.subList(Math.min(5, overall.size()), Math.min(10, overall.size()))) {
            Double r = rarity.apply(e.getKey());
            if (r != null && r > bestRarity) {
                bestRarity = r;
                best = e.getKey();
            }
        }
        return best;
    }

    private static double hours(double minutes) {
        return Math.round(minutes / 60.0 * 10) / 10.0;
    }

    private static double ratio(double r) {
        return Math.round(r * 1000) / 1000.0;
    }

    private static int pct(double r) {
        return (int) Math.round(r * 100);
    }
}
