package SteamGame.recommend.controller;

import SteamGame.recommend.domain.game.dto.GameNameRequestDTO;
import SteamGame.recommend.domain.game.dto.GameRequestDTO;
import SteamGame.recommend.domain.game.dto.SteamIdRequestDTO;
import SteamGame.recommend.domain.game.dto.TextRequestDTO;
import SteamGame.recommend.domain.recommendation.dto.RecommendationResultDTO;
import SteamGame.recommend.domain.recommendation.service.RecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "recommend", description = "Game Recommendation API")
@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @Operation(summary = "Tag-based random recommendation")
    @ApiResponse(responseCode = "200", description = "Success")
    @PostMapping("/random")
    public ResponseEntity<RecommendationResultDTO> randomGame(@Valid @RequestBody GameRequestDTO request) {
        try {
            log.info("Tag-based random recommendation request: tags={}, review={}, korean={}, free={}",
                    request.getTags(), request.getReview(), request.getKorean_check(), request.getFree_check());

            Boolean excludedCheck = request.getExcludedTag() != null;

            RecommendationResultDTO recommendServiceGame = recommendService.findGame(
                    request.getTags(),
                    request.getReview(),
                    request.getKorean_check(),
                    request.getFree_check(),
                    excludedCheck,
                    request.getExcludedTag()
            );

            log.info("Tag-based random recommendation successful: found {} games", recommendServiceGame.getRecommendedGame().size());
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

            RecommendationResultDTO result = recommendService.selectInfo(
                    dto.getText(),
                    dto.getReview(),
                    dto.getKorean_check(),
                    dto.getFree_check()
            );

            log.info("Text input-based recommendation successful: extracted tags={}, found {} games",
                    result.getUsedTags(), result.getRecommendedGame().size());
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

            RecommendationResultDTO result = recommendService.recommendByProfile(
                    dto.getSteamId(),
                    dto.getReview(),
                    dto.getKorean_check(),
                    dto.getFree_check()
            );

            log.info("Profile-based recommendation successful: steamId={}, extracted tags={}, found {} games",
                    dto.getSteamId(), result.getUsedTags(), result.getRecommendedGame().size());
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

            RecommendationResultDTO result = recommendService.recommendByRecentPlay(
                    dto.getSteamId(),
                    dto.getReview(),
                    dto.getKorean_check(),
                    dto.getFree_check()
            );

            log.info("Recent play-based recommendation successful: steamId={}, extracted tags={}, found {} games",
                    dto.getSteamId(), result.getUsedTags(), result.getRecommendedGame().size());
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

            RecommendationResultDTO result = recommendService.recommendBySimilarGame(
                    dto.getGameName(),
                    dto.getReview(),
                    dto.getKorean_check(),
                    dto.getFree_check()
            );

            log.info("Similar game recommendation successful: gameName={}, extracted tags={}, found {} games\"",
                    dto.getGameName(), result.getUsedTags(), result.getRecommendedGame().size());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Similar game recommendation error: gameName={}", dto.getGameName(), e);
            throw e;
        }
    }
}