package SteamGame.recommend.controller;

import SteamGame.recommend.domain.game.dto.GameNameRequestDTO;
import SteamGame.recommend.domain.game.dto.GameRequestDTO;
import SteamGame.recommend.domain.game.dto.SteamIdRequestDTO;
import SteamGame.recommend.domain.game.dto.TextRequestDTO;
import SteamGame.recommend.domain.recommendation.dto.MatchRequestDTO;
import SteamGame.recommend.domain.recommendation.dto.MatchResultDTO;
import SteamGame.recommend.domain.recommendation.dto.RecommendationResultDTO;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.feedback.entity.FeedbackContext;
import SteamGame.recommend.domain.feedback.service.FeedbackSuppressionService;
import SteamGame.recommend.domain.impression.service.ImpressionService;
import SteamGame.recommend.domain.recommendation.service.MatchService;
import SteamGame.recommend.domain.recommendation.service.RecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@Slf4j
@Tag(name = "recommend", description = "Game Recommendation API")
@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    private final RecommendService recommendService;
    private final MatchService matchService;
    private final FeedbackSuppressionService suppressionService;
    private final ImpressionService impressionService;

    public RecommendController(RecommendService recommendService, MatchService matchService,
                              FeedbackSuppressionService suppressionService,
                              ImpressionService impressionService) {
        this.recommendService = recommendService;
        this.matchService = matchService;
        this.suppressionService = suppressionService;
        this.impressionService = impressionService;
    }

    // client_id 로 DOWN 을 병합한 제외 집합으로 호출하고, 억제가 실제로 더해졌는데 404 면 억제를 떼고 한 번 재시도함
    // 클라이언트 자신의 제외나 태그 조건 때문에 원래 비는 경우는 재시도해도 같은 404 라 그대로 표면화됨
    private RecommendationResultDTO withSuppressionFallback(String clientId, Set<Long> clientExcludes,
                                                            Function<Set<Long>, RecommendationResultDTO> call) {
        Set<Long> effective = suppressionService.resolveExcludes(clientId, clientExcludes);
        int originalSize = (clientExcludes == null) ? 0 : clientExcludes.size();
        boolean suppressed = effective.size() > originalSize;
        try {
            return call.apply(effective);
        } catch (ResponseStatusException e) {
            if (suppressed && e.getStatusCode().value() == 404) {
                return call.apply(clientExcludes);
            }
            throw e;
        }
    }

    // 노출된 게임을 비동기로 적재한다, client_id 가 없거나 게임이 없으면 서비스에서 걸러진다
    // appid 가 없는 게임은 걸러 한 건 때문에 배치 전체가 실패하지 않게 한다
    private void logImpressions(FeedbackContext context, String clientId, String steamId, List<SteamAppDTO> games) {
        if (games == null) {
            return;
        }
        List<Long> appids = games.stream()
                .map(SteamAppDTO::getAppid)
                .filter(Objects::nonNull)
                .toList();
        impressionService.record(clientId, steamId, context, appids);
    }

    @Operation(summary = "Tag-based random recommendation")
    @ApiResponse(responseCode = "200", description = "Success")
    @PostMapping("/random")
    public ResponseEntity<RecommendationResultDTO> randomGame(@Valid @RequestBody GameRequestDTO request) {
        try {
            log.info("Tag-based random recommendation request: tags={}, review={}, korean={}, free={}",
                    request.getTags(), request.getReview(), request.getKorean_check(), request.getFree_check());

            Boolean excludedCheck = request.getExcludedTag() != null;
            // tags 를 빼고 보낸 요청은 태그 조건 없음으로 본다, null 이면 태그 목록 변환에서 NPE 로 500 이 됨
            String[] tags = request.getTags() != null ? request.getTags() : new String[0];

            RecommendationResultDTO recommendServiceGame = withSuppressionFallback(
                    request.getClientId(), request.getExcludeAppids(),
                    ex -> recommendService.findGame(
                            tags,
                            request.getReview(),
                            request.getKorean_check(),
                            request.getFree_check(),
                            excludedCheck,
                            request.getExcludedTag(),
                            ex));

            log.info("Tag-based random recommendation successful: found {} games", recommendServiceGame.getRecommendedGame().size());
            logImpressions(FeedbackContext.RANDOM, request.getClientId(), null, recommendServiceGame.getRecommendedGame());
            return ResponseEntity.ok(recommendServiceGame);

        } catch (Exception e) {
            log.error("Tag-based random recommendation error", e);
            throw e;
        }
    }

    @Operation(summary = "Text input-based recommendation")
    @ApiResponse(responseCode = "200", description = "Success")
    @PostMapping("/input")
    public ResponseEntity<RecommendationResultDTO> inputRandomGame(@Valid @RequestBody TextRequestDTO dto) {
        try {
            log.info("Text input-based recommendation request: text={}, review={}, korean={}, free={}",
                    dto.getText(), dto.getReview(), dto.getKorean_check(), dto.getFree_check());

            RecommendationResultDTO result = withSuppressionFallback(
                    dto.getClientId(), dto.getExcludeAppids(),
                    ex -> recommendService.selectInfo(
                            dto.getText(),
                            dto.getReview(),
                            dto.getKorean_check(),
                            dto.getFree_check(),
                            ex));

            log.info("Text input-based recommendation successful: extracted tags={}, found {} games",
                    result.getUsedTags(), result.getRecommendedGame().size());
            logImpressions(FeedbackContext.INPUT, dto.getClientId(), null, result.getRecommendedGame());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Text input-based recommendation error: text={}", dto.getText(), e);
            throw e;
        }
    }

    @Operation(summary = "Profile-based game recommendation")
    @ApiResponse(responseCode = "200", description = "Success")
    @PostMapping("/profile")
    public ResponseEntity<RecommendationResultDTO> randomGameByProfile(@Valid @RequestBody SteamIdRequestDTO dto) {
        try {
            log.info("Profile-based recommendation request: steamId={}, review={}, korean={}, free={}",
                    dto.getSteamId(), dto.getReview(), dto.getKorean_check(), dto.getFree_check());

            RecommendationResultDTO result = withSuppressionFallback(
                    dto.getClientId(), dto.getExcludeAppids(),
                    ex -> recommendService.recommendByProfile(
                            dto.getSteamId(),
                            dto.getReview(),
                            dto.getKorean_check(),
                            dto.getFree_check(),
                            ex));

            log.info("Profile-based recommendation successful: steamId={}, extracted tags={}, found {} games",
                    dto.getSteamId(), result.getUsedTags(), result.getRecommendedGame().size());
            logImpressions(FeedbackContext.PROFILE, dto.getClientId(), dto.getSteamId(), result.getRecommendedGame());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Profile-based recommendation error: steamId={}", dto.getSteamId(), e);
            throw e;
        }
    }

    @Operation(summary = "Recent 2-week playtime-based recommendation")
    @ApiResponse(responseCode = "200", description = "Success")
    @PostMapping("/recent-play")
    public ResponseEntity<RecommendationResultDTO> randomGameByRecentPlay(@Valid @RequestBody SteamIdRequestDTO dto) {
        try {
            log.info("Recent play-based recommendation request: steamId={}, review={}, korean={}, free={}",
                    dto.getSteamId(), dto.getReview(), dto.getKorean_check(), dto.getFree_check());

            RecommendationResultDTO result = withSuppressionFallback(
                    dto.getClientId(), dto.getExcludeAppids(),
                    ex -> recommendService.recommendByRecentPlay(
                            dto.getSteamId(),
                            dto.getReview(),
                            dto.getKorean_check(),
                            dto.getFree_check(),
                            ex));

            log.info("Recent play-based recommendation successful: steamId={}, extracted tags={}, found {} games",
                    dto.getSteamId(), result.getUsedTags(), result.getRecommendedGame().size());
            logImpressions(FeedbackContext.RECENT, dto.getClientId(), dto.getSteamId(), result.getRecommendedGame());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Recent play-based recommendation error: steamId={}", dto.getSteamId(), e);
            throw e;
        }
    }

    @Operation(summary = "Similar game recommendation based on selected game")
    @ApiResponse(responseCode = "200", description = "Success")
    @PostMapping("/similar")
    public ResponseEntity<RecommendationResultDTO> randomGameBySimilarGame(@Valid @RequestBody GameNameRequestDTO dto) {
        try {
            log.info("Similar game recommendation request: gameName={}, review={}, korean={}, free={}",
                    dto.getGameName(), dto.getReview(), dto.getKorean_check(), dto.getFree_check());

            RecommendationResultDTO result = withSuppressionFallback(
                    dto.getClientId(), dto.getExcludeAppids(),
                    ex -> recommendService.recommendBySimilarGame(
                            dto.getGameName(),
                            dto.getReview(),
                            dto.getKorean_check(),
                            dto.getFree_check(),
                            ex));

            log.info("Similar game recommendation successful: gameName={}, extracted tags={}, found {} games\"",
                    dto.getGameName(), result.getUsedTags(), result.getRecommendedGame().size());
            logImpressions(FeedbackContext.SIMILAR, dto.getClientId(), null, result.getRecommendedGame());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Similar game recommendation error: gameName={}", dto.getGameName(), e);
            throw e;
        }
    }

    @Operation(summary = "Requirement-matched recommendation")
    @ApiResponse(responseCode = "200", description = "Success")
    @PostMapping("/match")
    public ResponseEntity<MatchResultDTO> match(@Valid @RequestBody MatchRequestDTO req) {
        MatchResultDTO result = matchService.recommend(req);
        logImpressions(FeedbackContext.MATCH, req.getClientId(), null, result.games());
        return ResponseEntity.ok(result);
    }
}