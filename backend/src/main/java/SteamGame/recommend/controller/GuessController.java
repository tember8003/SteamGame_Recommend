package SteamGame.recommend.controller;

import SteamGame.recommend.domain.guess.dto.*;
import SteamGame.recommend.domain.guess.service.GuessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "reviewGuess", description = "Review Guess Game API")
@RestController
@RequestMapping("/api/review-guess")
public class GuessController {

    private final GuessService guessService;

    public GuessController(GuessService guessService) {
        this.guessService = guessService;
    }

    @Operation(summary = "Start review guess game session")
    @ApiResponse(responseCode = "200", description = "Success")
    @PostMapping("/start")
    public ResponseEntity<ReviewGuessStartResponseDTO> startGame() {
        try {
            log.info("Review guess game start request");

            ReviewGuessStartResponseDTO response = guessService.startGame();

            log.info("Review guess game started successfully with {} rounds", response.getRounds().size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Review guess game start error", e);
            throw e;
        }
    }

    @Operation(summary = "Submit answer for current round")
    @ApiResponse(responseCode = "200", description = "Success")
    @PostMapping("/answer")
    public ResponseEntity<ReviewGuessAnswerResponseDTO> submitAnswer(@Valid @RequestBody ReviewGuessAnswerRequestDTO request) {
        try {
            log.info("Review guess answer submission: sessionId={}, roundIndex={}, selectedGameId={}",
                    request.getSessionId(), request.getRoundIndex(), request.getSelectedGameId());

            ReviewGuessAnswerResponseDTO response = guessService.submitAnswer(
                    request.getSessionId(),
                    request.getRoundIndex(),
                    request.getSelectedGameId()
            );

            log.info("Review guess answer processed successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Review guess answer error: sessionId={}, roundIndex={}",
                    request.getSessionId(), request.getRoundIndex(), e);
            throw e;
        }
    }

    @Operation(summary = "Get final game results")
    @ApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/{sessionId}/result")
    public ResponseEntity<ReviewGuessResultDTO> getGameResult(@PathVariable String sessionId) {
        try {
            log.info("Review guess result request: sessionId={}", sessionId);

            ReviewGuessResultDTO result = guessService.getGameResult(sessionId);

            log.info("Review guess result generated: sessionId={}, score={}/{}",
                    sessionId, result.getCorrectAnswers(), result.getTotalRounds());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Review guess result error: sessionId={}", sessionId, e);
            throw e;
        }
    }

    @Operation(summary = "Get game session status")
    @ApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/{sessionId}/status")
    public ResponseEntity<ReviewGuessStatusDTO> getGameStatus(@PathVariable String sessionId) {
        try {
            log.info("Review guess status request: sessionId={}", sessionId);

            ReviewGuessStatusDTO status = guessService.getGameStatus(sessionId);

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Review guess status error: sessionId={}", sessionId, e);
            throw e;
        }
    }
}