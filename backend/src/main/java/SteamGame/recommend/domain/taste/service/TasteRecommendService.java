package SteamGame.recommend.domain.taste.service;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.mapper.GameMapper;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.recommendation.dto.ExplanationItem;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.taste.content.TagWeight;
import SteamGame.recommend.domain.taste.content.TasteContentLoader;
import SteamGame.recommend.domain.taste.content.TasteType;
import SteamGame.recommend.domain.taste.repository.TasteCandidate;
import SteamGame.recommend.domain.taste.repository.TasteCandidateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Slf4j
@Service
public class TasteRecommendService {

    static final int CANDIDATE_COUNT = 60;
    static final int RESULT_SIZE = 5;
    static final int MAX_PER_LEAD_TAG = 2;
    static final int EXPLAIN_TAGS = 3;

    private final TasteContentLoader contentLoader;
    private final TasteCandidateRepository candidateRepository;
    private final GameRepository gameRepository;
    private final TagRarityCache rarityCache;

    public TasteRecommendService(TasteContentLoader contentLoader, TasteCandidateRepository candidateRepository,
                                 GameRepository gameRepository, TagRarityCache rarityCache) {
        this.contentLoader = contentLoader;
        this.candidateRepository = candidateRepository;
        this.gameRepository = gameRepository;
        this.rarityCache = rarityCache;
    }

    public TasteRecommendResult recommend(String code, int review, Boolean korean, Boolean free, Set<Long> excludeAppids) {
        TasteType type = contentLoader.content().type(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TASTE_TYPE_NOT_FOUND"));

        List<String> tags = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (TagWeight tw : type.tagWeights()) {
            tags.add(tw.tag());
            weights.add(tw.weight() * rarityCache.rarity(tw.tag()));
        }
        // 대표 게임은 이미 첫 화면에 나가므로 동적 추천에서 뺀다
        Set<Long> exclude = new HashSet<>(excludeAppids);
        exclude.addAll(type.featuredAppids());

        List<TasteCandidate> ranked = candidateRepository.findCandidates(
                tags, weights, review, korean, free, exclude, CANDIDATE_COUNT);
        List<TasteCandidate> picked = DiversityPicker.pick(ranked, RESULT_SIZE, MAX_PER_LEAD_TAG, new Random());

        List<Long> pickedAppids = picked.stream().map(TasteCandidate::appid).toList();
        Map<Long, Game> byAppid = new HashMap<>();
        if (!pickedAppids.isEmpty()) {
            for (Game g : gameRepository.findByAppidIn(pickedAppids)) {
                byAppid.put(g.getAppid(), g);
            }
        }

        List<SteamAppDTO> games = new ArrayList<>();
        List<GameExplanation> explanations = new ArrayList<>();
        for (TasteCandidate c : picked) {
            Game g = byAppid.get(c.appid());
            if (g == null) {
                log.warn("taste candidate missing in games: appid={}", c.appid());
                continue;
            }
            games.add(GameMapper.convertToSteamDTO(g));
            List<String> top = c.matchedTags().subList(0, Math.min(EXPLAIN_TAGS, c.matchedTags().size()));
            explanations.add(new GameExplanation(c.appid(),
                    List.of(new ExplanationItem("TASTE_TAG_MATCH", Map.of("tags", List.copyOf(top))))));
        }
        if (games.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No games found for this taste type.");
        }
        return new TasteRecommendResult(games, explanations);
    }

    // 대표 게임, 문구 데이터의 appid 순서를 지키고 DB에 없는 것은 뺀다
    public List<SteamAppDTO> featuredGames(TasteType type) {
        List<Long> appids = type.featuredAppids();
        Map<Long, Game> byAppid = new HashMap<>();
        for (Game g : gameRepository.findByAppidIn(appids)) {
            byAppid.put(g.getAppid(), g);
        }
        List<SteamAppDTO> result = new ArrayList<>();
        for (Long appid : appids) {
            Game g = byAppid.get(appid);
            if (g == null) {
                log.warn("featured game missing in games: type={}, appid={}", type.code(), appid);
                continue;
            }
            result.add(GameMapper.convertToSteamDTO(g));
        }
        return result;
    }
}
