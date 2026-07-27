package SteamGame.recommend.controller;

import SteamGame.recommend.domain.game.dto.GameSearchResponseDTO;
import SteamGame.recommend.domain.game.service.GameService;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@Slf4j
@Tag(name = "game", description = "Game-related API")
@RestController
@RequestMapping("/api/games")
@Validated
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @Operation(summary = "Get game by AppId")
    @ApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/appid")
    public ResponseEntity<SteamAppDTO> getGameByAppid(
            @RequestParam @Positive(message = "AppId must be a positive number") long appid) {
        try {
            log.info("Game search by AppId request: appid={}", appid);

            SteamAppDTO game = gameService.findGameByAppid(appid);

            log.info("Game search by AppId successful: appid={}, gameName={}", appid, game.getName());
            return ResponseEntity.ok(game);

        } catch (Exception e) {
            log.error("Game search by AppId error: appid={}", appid, e);
            throw e;
        }
    }

    @Operation(summary = "Get all available tags list")
    @ApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/tags")
    public ResponseEntity<List<String>> getTags() {
        try {
            log.info("Get all tags request");

            List<String> tags = gameService.getTags();

            log.info("Get all tags successful: found {} tags", tags.size());
            return ResponseEntity.ok(tags);

        } catch (Exception e) {
            log.error("Get all tags error", e);
            throw e;
        }
    }

    @Operation(summary = "Search games by name")
    @ApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/search")
    public ResponseEntity<List<GameSearchResponseDTO>> searchGameNames(
            @RequestParam("query") @NotBlank(message = "Search query cannot be empty") String query) {
        try {
            log.info("Game search by name request: query={}", query);

            if (query == null || query.trim().isEmpty()) {
                log.warn("Empty search query provided");
                return ResponseEntity.ok(Collections.emptyList());
            }

            String trimmedQuery = query.trim();
            List<GameSearchResponseDTO> games = gameService.searchNames(trimmedQuery);

            log.info("Game search by name successful: query={}, found {} games", trimmedQuery, games.size());
            return ResponseEntity.ok(games);

        } catch (Exception e) {
            log.error("Game search by name error: query={}", query, e);
            throw e;
        }
    }
}