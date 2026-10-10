package SteamGame.recommend.domain.recommendation.similar;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.impression.entity.ImpressionSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 두 순위기가 번갈아 한 개씩 고른다, 고른 쪽을 그 게임의 출처로 남긴다
public final class TeamDraftInterleaver {

    private TeamDraftInterleaver() {}

    // 각자 자기 순서에서 아직 안 뽑혔고 판본도 겹치지 않는 맨 앞 게임을 고른다
    // 시드 게임의 다른 판본도 건너뛴다, 한쪽이 바닥나면 다른 쪽이 남은 자리를 채운다
    public static List<InterleavedPick> interleave(List<Game> gemini, List<Game> gnn, String seedName,
                                                   int need, boolean geminiFirst) {
        Set<Long> pickedIds = new HashSet<>();
        Set<String> pickedNames = new HashSet<>();
        String seed = NameNormalizer.normalize(seedName);
        if (!seed.isEmpty()) {
            pickedNames.add(seed);
        }
        List<InterleavedPick> picks = new ArrayList<>();
        int geminiFrom = 0;
        int gnnFrom = 0;
        boolean geminiTurn = geminiFirst;
        while (picks.size() < need) {
            int g = nextEligible(gemini, geminiFrom, pickedIds, pickedNames);
            int n = nextEligible(gnn, gnnFrom, pickedIds, pickedNames);
            if (g < 0 && n < 0) {
                break;
            }
            boolean takeGemini = n < 0 || (geminiTurn && g >= 0);
            Game chosen = takeGemini ? gemini.get(g) : gnn.get(n);
            picks.add(new InterleavedPick(chosen, takeGemini ? ImpressionSource.GEMINI : ImpressionSource.GNN));
            pickedIds.add(chosen.getAppid());
            pickedNames.add(NameNormalizer.normalize(chosen.getName()));
            if (takeGemini) {
                geminiFrom = g + 1;
            } else {
                gnnFrom = n + 1;
            }
            geminiTurn = !geminiTurn;
        }
        return picks;
    }

    private static int nextEligible(List<Game> games, int from, Set<Long> pickedIds, Set<String> pickedNames) {
        for (int i = from; i < games.size(); i++) {
            Game game = games.get(i);
            if (!pickedIds.contains(game.getAppid()) && !pickedNames.contains(NameNormalizer.normalize(game.getName()))) {
                return i;
            }
        }
        return -1;
    }
}
