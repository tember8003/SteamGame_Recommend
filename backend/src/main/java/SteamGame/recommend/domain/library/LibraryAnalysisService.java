package SteamGame.recommend.domain.library;

import SteamGame.recommend.domain.library.analysis.LibraryAnalysis;
import SteamGame.recommend.domain.library.analysis.LibraryAnalyzer;
import SteamGame.recommend.domain.library.content.LibraryContent;
import SteamGame.recommend.domain.library.content.LibraryContentLoader;
import SteamGame.recommend.domain.library.share.LibraryShareService;
import SteamGame.recommend.domain.library.steam.OwnedGame;
import SteamGame.recommend.domain.library.steam.SteamIdResolver;
import SteamGame.recommend.domain.library.steam.SteamLibraryClient;
import SteamGame.recommend.domain.library.steam.SteamLibraryResult;
import SteamGame.recommend.domain.library.steam.SteamProfile;
import SteamGame.recommend.domain.tag.repository.TagRepository;
import SteamGame.recommend.domain.taste.service.TagRarityCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import SteamGame.recommend.domain.library.dto.LibraryViewMapper;
import SteamGame.recommend.domain.library.dto.SharedAnalysisResponse;
import SteamGame.recommend.domain.taste.content.Lang;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 라이브러리 분석 흐름: 입력 해석, Steam 읽기, 태그 매칭, 분석, 분석 결과 임시 보관, 공유 저장
// 로그에는 SteamID64 끝 4자리만 남기고 닉네임과 게임 목록은 남기지 않는다
@Service
public class LibraryAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(LibraryAnalysisService.class);
    private static final int TAG_QUERY_CHUNK = 1000;
    // 공유 스냅샷 저장 형식 번호, 모양이 바뀌면 올린다
    private static final int SNAPSHOT_VERSION = 1;

    // profile은 분석 응답에만 쓰고 보관하거나 공유에 넣지 않는다
    public record Analyzed(String analysisId, SteamProfile profile, LibraryAnalysis analysis) {
    }

    private final SteamIdResolver resolver;
    private final SteamLibraryClient client;
    private final TagRepository tagRepository;
    private final TagRarityCache rarityCache;
    private final LibraryContentLoader contentLoader;
    private final LibraryShareService shareService;
    private final ObjectMapper objectMapper;
    // 분석 결과만 둔다, SteamID64와 프로필은 넣지 않는다
    private final Cache<String, LibraryAnalysis> analyses;

    @Autowired
    public LibraryAnalysisService(SteamIdResolver resolver, SteamLibraryClient client, TagRepository tagRepository,
                                  TagRarityCache rarityCache, LibraryContentLoader contentLoader,
                                  LibraryShareService shareService, ObjectMapper objectMapper) {
        this(resolver, client, tagRepository, rarityCache, contentLoader, shareService, objectMapper,
                Ticker.systemTicker());
    }

    // 테스트에서 가짜 시계를 넣기 위한 생성자
    LibraryAnalysisService(SteamIdResolver resolver, SteamLibraryClient client, TagRepository tagRepository,
                           TagRarityCache rarityCache, LibraryContentLoader contentLoader,
                           LibraryShareService shareService, ObjectMapper objectMapper, Ticker ticker) {
        this.resolver = resolver;
        this.client = client;
        this.tagRepository = tagRepository;
        this.rarityCache = rarityCache;
        this.contentLoader = contentLoader;
        this.shareService = shareService;
        this.objectMapper = objectMapper;
        this.analyses = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(30))
                .maximumSize(10_000)
                .ticker(ticker)
                .build();
    }

    public Analyzed analyze(String steamInput) {
        // 17자리 숫자 해석 실패(400, 404)와 사용자 지정 주소 조회 장애(503)는 이미 상태가 정해져 있어 감싸지 않는다
        String steamId64 = resolver.resolve(steamInput);
        SteamLibraryResult result = client.fetch(steamId64);

        if (result instanceof SteamLibraryResult.Private) {
            throw status(HttpStatus.FORBIDDEN, "LIBRARY_PRIVATE");
        }
        if (result instanceof SteamLibraryResult.NotFound) {
            throw status(HttpStatus.NOT_FOUND, "STEAM_PROFILE_NOT_FOUND");
        }
        if (result instanceof SteamLibraryResult.Empty) {
            throw status(HttpStatus.UNPROCESSABLE_ENTITY, "LIBRARY_EMPTY");
        }
        if (result instanceof SteamLibraryResult.Unavailable) {
            // 클라이언트가 이미 원인을 로그로 남긴다
            // 기존 처리기가 5xx 사유를 응답에서 가리므로 프론트는 상태 코드 503으로 판단한다
            throw status(HttpStatus.SERVICE_UNAVAILABLE, "STEAM_UNAVAILABLE");
        }
        SteamLibraryResult.Found found = (SteamLibraryResult.Found) result;

        LibraryContent content = contentLoader.content();
        List<OwnedGame> games = found.games();
        LibraryAnalysis analysis = LibraryAnalyzer.analyze(games, tagsOf(games), rarityCache::rarity, content,
                steamId64);

        String analysisId = UUID.randomUUID().toString();
        analyses.put(analysisId, analysis);
        log.info("라이브러리 분석 완료 steam=***{} owned={} matched={} limited={}", tail(steamId64),
                analysis.stats().ownedCount(), analysis.stats().matchedCount(), analysis.limitedData());
        return new Analyzed(analysisId, found.profile(), analysis);
    }

    // 닉네임, 아바타, SteamID64가 없는 분석 결과만 버전 표식과 함께 JSON으로 저장한다, 언어는 읽을 때 고른다
    public String share(String analysisId) {
        LibraryAnalysis analysis = analyses.getIfPresent(analysisId);
        if (analysis == null) {
            throw status(HttpStatus.NOT_FOUND, "ANALYSIS_EXPIRED");
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("v", SNAPSHOT_VERSION);
        root.set("analysis", objectMapper.valueToTree(analysis));
        return shareService.save(root.toString());
    }

    // 읽기, 복원, 렌더링을 한 번에 한다, 옛 스냅샷이 지금 모양과 맞지 않으면 500 대신 없는 것으로 본다
    public SharedAnalysisResponse renderShared(String shareId, Lang lang) {
        String payload = shareService.find(shareId)
                .orElseThrow(() -> status(HttpStatus.NOT_FOUND, "SHARE_NOT_FOUND"));
        try {
            JsonNode root = objectMapper.readTree(payload);
            // v가 없는 저장분은 v1(분석 결과가 바로 최상위에 있는 모양)이다
            JsonNode body = root.has("v") ? root.get("analysis") : root;
            LibraryAnalysis analysis = objectMapper.treeToValue(body, LibraryAnalysis.class);
            return LibraryViewMapper.toSharedResponse(analysis, contentLoader.content(), lang);
        } catch (Exception e) {
            log.error("library snapshot unreadable shareId={}", shareId, e);
            throw status(HttpStatus.NOT_FOUND, "SHARE_NOT_FOUND");
        }
    }

    // appid별 태그 이름, 게임이 많은 계정도 IN 절이 커지지 않게 나눠서 읽는다
    private Map<Long, List<String>> tagsOf(List<OwnedGame> games) {
        List<Long> appids = games.stream().map(OwnedGame::appid).distinct().toList();
        Map<Long, List<String>> tags = new HashMap<>();
        for (int from = 0; from < appids.size(); from += TAG_QUERY_CHUNK) {
            List<Long> chunk = new ArrayList<>(appids.subList(from, Math.min(from + TAG_QUERY_CHUNK, appids.size())));
            for (Map<String, Object> row : tagRepository.findGameTagMapping(chunk)) {
                Long appid = ((Number) row.get("appId")).longValue();
                tags.computeIfAbsent(appid, k -> new ArrayList<>()).add((String) row.get("tagName"));
            }
        }
        return tags;
    }

    private static ResponseStatusException status(HttpStatus status, String reason) {
        return new ResponseStatusException(status, reason);
    }

    private static String tail(String steamId64) {
        return steamId64.length() <= 4 ? steamId64 : steamId64.substring(steamId64.length() - 4);
    }
}
