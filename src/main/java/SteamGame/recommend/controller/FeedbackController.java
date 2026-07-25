package SteamGame.recommend.controller;

import SteamGame.recommend.domain.feedback.dto.FeedbackRequestDTO;
import SteamGame.recommend.domain.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "feedback", description = "Recommendation Feedback Logging API")
@RestController
@RequestMapping("/api/recommend")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Operation(summary = "Log recommendation feedback (up/down/click)")
    @ApiResponse(responseCode = "204", description = "Recorded")
    @PostMapping("/feedback")
    public ResponseEntity<Void> submitFeedback(@Valid @RequestBody FeedbackRequestDTO dto) {
        feedbackService.record(dto);
        return ResponseEntity.noContent().build();
    }
}
