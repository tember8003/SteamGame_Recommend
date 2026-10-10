package SteamGame.recommend.domain.recommendation.similar;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.mapper.GameMapper;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.infra.embedding.EmbeddingProperties;
import SteamGame.recommend.infra.embedding.EmbeddingRepository;
import SteamGame.recommend.infra.embedding.VectorLiterals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

// 설명문 임베딩 순위기와 태그 구조 임베딩 순위기의 결과를 섞어 비슷한 게임을 고른다
@Service
public class SimilarInterleaveRecommender {

    private static final Logger log = LoggerFactory.getLogger(SimilarInterleaveRecommender.class);

    // 실험에서 정한 값, 최근접 30개를 인기와 섞어 다시 줄 세우고 상위 10개 안에서 고른다
    static final int GNN_CANDIDATES = 30;
    static final int GNN_POOL = 10;

    private final GameRepository gameRepository;
    private final EmbeddingRepository embeddingRepository;
    private final EmbeddingProperties embeddingProperties;
    private final Supplier<Random> randomSupplier;

    @Autowired
    public SimilarInterleaveRecommender(GameRepository gameRepository, EmbeddingRepository embeddingRepository,
                                        EmbeddingProperties embeddingProperties) {
        this(gameRepository, embeddingRepository, embeddingProperties, Random::new);
    }

    SimilarInterleaveRecommender(GameRepository gameRepository, EmbeddingRepository embeddingRepository,
                                 EmbeddingProperties embeddingProperties, Supplier<Random> randomSupplier) {
        this.gameRepository = gameRepository;
        this.embeddingRepository = embeddingRepository;
        this.embeddingProperties = embeddingProperties;
        this.randomSupplier = randomSupplier;
    }

    // exclude 에는 시드 appid 가 들어 있어야 한다, 두 순위기가 모두 비면 빈 목록을 돌려준다
    public List<SteamAppDTO> recommend(long seedAppid, String seedName, int review, Boolean korean, Boolean free,
                                       Set<Long> exclude) {
        Random random = randomSupplier.get();
        // 빈 집합이면 sentinel [-1] 바인딩 + excludeCount=0 으로 NOT IN 을 무력화한다
        List<Long> excludeList = exclude.isEmpty() ? List.of(-1L) : new ArrayList<>(exclude);
        int excludeCount = exclude.size();

        List<Game> gemini = geminiOrder(seedAppid, review, korean, free, excludeList, excludeCount, random);
        List<Game> gnn = gnnOrderOrEmpty(seedAppid, review, korean, free, excludeList, excludeCount, random);
        List<InterleavedPick> picks = TeamDraftInterleaver.interleave(
                gemini, gnn, seedName, embeddingProperties.getResultSize(), random.nextBoolean());

        return picks.stream().map(pick -> {
            SteamAppDTO dto = GameMapper.convertToSteamDTO(pick.game());
            dto.setSource(pick.source());
            return dto;
        }).toList();
    }

    // 지금 비슷한 게임 추천과 같은 후보를 무작위로 섞는다, 다시 추천할 때마다 결과가 바뀌게 한다
    private List<Game> geminiOrder(long seedAppid, int review, Boolean korean, Boolean free,
                                   List<Long> excludeList, int excludeCount, Random random) {
        float[] seed = embeddingRepository.findEmbeddingByAppid(seedAppid);
        if (seed == null) {
            return List.of();
        }
        List<Game> pool = new ArrayList<>(gameRepository.findNearestByVector(VectorLiterals.toPgVector(seed),
                review, korean, free, excludeList, excludeCount, false, List.of(),
                embeddingProperties.getCandidatePoolK()));
        Collections.shuffle(pool, random);
        return pool;
    }

    // GNN 쪽 조회가 실패하면 빈 목록으로 두고 설명문 쪽 결과만 낸다, 요청 전체를 실패시키지 않는다
    private List<Game> gnnOrderOrEmpty(long seedAppid, int review, Boolean korean, Boolean free,
                                       List<Long> excludeList, int excludeCount, Random random) {
        try {
            return gnnOrder(seedAppid, review, korean, free, excludeList, excludeCount, random);
        } catch (RuntimeException e) {
            log.warn("태그 구조 순위기 조회 실패, 설명문 결과만 씀 seedAppid={}", seedAppid, e);
            return List.of();
        }
    }

    private List<Game> gnnOrder(long seedAppid, int review, Boolean korean, Boolean free,
                                List<Long> excludeList, int excludeCount, Random random) {
        String seed = embeddingRepository.findTagEmbeddingText(seedAppid);
        if (seed == null) {
            return List.of();
        }
        List<Game> ranked = GnnRanker.rankByPopularityBlend(gameRepository.findNearestByTagVector(
                seed, review, korean, free, excludeList, excludeCount, GNN_CANDIDATES));
        List<Game> pool = new ArrayList<>(ranked.subList(0, Math.min(GNN_POOL, ranked.size())));
        Collections.shuffle(pool, random);
        return pool;
    }
}
