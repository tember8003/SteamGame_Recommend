package SteamGame.recommend.controller;

import SteamGame.recommend.domain.choice.dto.*;
import SteamGame.recommend.domain.choice.service.GameChoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "choice", description = "Game Choice API")
@RestController
@RequestMapping("/api/choice")
public class ChoiceController {

    private final GameChoiceService gameChoiceService;

    ChoiceController(GameChoiceService gameChoiceService) {
        this.gameChoiceService = gameChoiceService;
    }

    @Operation(summary = "Start game choice session")
    @ApiResponse(responseCode = "200", description = "Success")
    @PostMapping("/start")
    public ResponseEntity<ChoiceStartResponseDTO> startChoice(@Valid @RequestBody ChoiceStartRequestDTO request) {
        try {
            log.info("Choice start request: review={}, korean={}, free={}",
                    request.getReview(), request.getKorean_check(), request.getFree_check());

            ChoiceStartResponseDTO response = gameChoiceService.startChoice();

            log.info("Choice started successfully with {} rounds", response.getRounds().size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Choice start error", e);
            throw e;
        }
    }

    @Operation(summary = "Submit choice selection")
    @ApiResponse(responseCode = "200", description = "Success")
    @PostMapping("/select")
    public ResponseEntity<ChoiceSelectionResponseDTO> submitSelection(@Valid @RequestBody ChoiceSelectionRequestDTO request) {
        try {
            log.info("Choice selection submission: sessionId={}, roundIndex={}, selectedGameId={}",
                    request.getSessionId(), request.getRoundIndex(), request.getSelectedGameId());

            ChoiceSelectionResponseDTO response = gameChoiceService.submitSelection(
                    request.getSessionId(),
                    request.getRoundIndex(),
                    request.getSelectedGameId()
            );

            log.info("Choice selection processed successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Choice selection error: sessionId={}, roundIndex={}",
                    request.getSessionId(), request.getRoundIndex(), e);
            throw e;
        }
    }

    @Operation(summary = "Get choice recommendations")
    @ApiResponse(responseCode = "200", description = "Success")
    @PostMapping("/result")
    public ResponseEntity<ChoiceResultDTO> getChoiceResult(@Valid @RequestBody ChoiceResultRequestDTO request) {
        try {
            log.info("Choice result request: sessionId={}", request.getSessionId());

            ChoiceResultDTO result = gameChoiceService.getChoiceResult(
                    request.getSessionId(),
                    request.getReview(),
                    request.getKorean_check(),
                    request.getFree_check()
            );

            log.info("Choice result generated: found {} games, used tags={}",
                    result.getRecommendedGames().size(), result.getUsedTags());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Choice result error: sessionId={}", request.getSessionId(), e);
            throw e;
        }
    }

    @Operation(summary = "Get choice session status")
    @ApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/{sessionId}/status")
    public ResponseEntity<ChoiceStatusDTO> getChoiceStatus(@PathVariable String sessionId) {
        try {
            log.info("Choice status request: sessionId={}", sessionId);

            ChoiceStatusDTO status = gameChoiceService.getChoiceStatus(sessionId);

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Choice status error: sessionId={}", sessionId, e);
            throw e;
        }
    }
}
