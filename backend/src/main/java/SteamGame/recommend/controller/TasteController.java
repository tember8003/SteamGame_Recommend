package SteamGame.recommend.controller;

import SteamGame.recommend.domain.event.entity.FunnelEventType;
import SteamGame.recommend.domain.event.service.FunnelEventService;
import SteamGame.recommend.domain.feedback.entity.FeedbackContext;
import SteamGame.recommend.domain.feedback.service.FeedbackSuppressionService;
import SteamGame.recommend.domain.impression.service.ImpressionService;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.taste.content.Lang;
import SteamGame.recommend.domain.taste.content.TasteContent;
import SteamGame.recommend.domain.taste.content.TasteContentLoader;
import SteamGame.recommend.domain.taste.content.TasteType;
import SteamGame.recommend.domain.taste.dto.TasteQuestionsResponse;
import SteamGame.recommend.domain.taste.dto.TasteRecommendRequest;
import SteamGame.recommend.domain.taste.dto.TasteResultRequest;
import SteamGame.recommend.domain.taste.dto.TasteResultResponse;
import SteamGame.recommend.domain.taste.dto.TasteViewMapper;
import SteamGame.recommend.domain.taste.dto.TypeSummary;
import SteamGame.recommend.domain.taste.dto.TypeView;
import SteamGame.recommend.domain.taste.service.TasteRecommendResult;
import SteamGame.recommend.domain.taste.service.TasteRecommendService;
import SteamGame.recommend.domain.taste.service.TasteScore;
import SteamGame.recommend.domain.taste.service.TasteScorer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Tag(name = "taste", description = "Game taste test API")
@RestController
@RequestMapping("/api/taste")
public class TasteController {

    private static final int DEFAULT_REVIEW = 300;

    private final TasteContentLoader contentLoader;
    private final TasteRecommendService recommendService;
    private final FeedbackSuppressionService suppressionService;
    private final ImpressionService impressionService;
    private final FunnelEventService eventService;

    public TasteController(TasteContentLoader contentLoader, TasteRecommendService recommendService,
                           FeedbackSuppressionService suppressionService, ImpressionService impressionService,
                           FunnelEventService eventService) {
        this.contentLoader = contentLoader;
        this.recommendService = recommendService;
        this.suppressionService = suppressionService;
        this.impressionService = impressionService;
        this.eventService = eventService;
    }

    // 문구는 버전 안에서 바뀌지 않아 한 시간 캐시한다
    private static CacheControl cache() {
        return CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic();
    }

    private TasteType findType(String code) {
        return contentLoader.content().type(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TASTE_TYPE_NOT_FOUND"));
    }

    private static List<Long> appidsOf(List<SteamAppDTO> games) {
        return games.stream().map(SteamAppDTO::getAppid).filter(Objects::nonNull).toList();
    }

    @Operation(summary = "Taste test questions")
    @GetMapping("/questions")
    public ResponseEntity<TasteQuestionsResponse> questions(@RequestParam(required = false) String lang) {
        return ResponseEntity.ok().cacheControl(cache())
                .body(TasteViewMapper.toView(contentLoader.content(), Lang.from(lang)));
    }

    @Operation(summary = "All 16 type summaries")
    @GetMapping("/types")
    public ResponseEntity<List<TypeSummary>> types(@RequestParam(required = false) String lang) {
        Lang l = Lang.from(lang);
        List<TypeSummary> body = contentLoader.content().types().stream()
                .map(t -> TasteViewMapper.toSummary(t, l))
                .toList();
        return ResponseEntity.ok().cacheControl(cache()).body(body);
    }

    @Operation(summary = "One type in full")
    @GetMapping("/types/{code}")
    public ResponseEntity<TypeView> type(@PathVariable String code, @RequestParam(required = false) String lang) {
        TasteType type = findType(code);
        TypeView body = TasteViewMapper.toView(contentLoader.content(), type, Lang.from(lang),
                recommendService.featuredGames(type));
        return ResponseEntity.ok().cacheControl(cache()).body(body);
    }

    @Operation(summary = "Compute the type from 12 answers")
    @PostMapping("/result")
    public ResponseEntity<TasteResultResponse> result(@Valid @RequestBody TasteResultRequest request,
                                                      @RequestParam(required = false) String lang) {
        TasteContent content = contentLoader.content();
        if (!content.version().equals(request.version())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TASTE_VERSION_MISMATCH");
        }
        TasteScore score;
        try {
            score = TasteScorer.score(content, request.answers());
        } catch (IllegalArgumentException e) {
            // 기존 처리기는 IllegalArgumentException을 500으로 만들어 여기서 400으로 바꾼다
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ANSWERS");
        }
        TasteType type = findType(score.code());
        List<SteamAppDTO> featured = recommendService.featuredGames(type);

        eventService.record(request.clientId(), FunnelEventType.TEST_COMPLETE, type.code(), null);
        // 결과 화면에 보이는 대표 게임의 노출을 남겨 그 카드의 클릭과 짝을 맞춘다
        impressionService.record(request.clientId(), null, FeedbackContext.TASTE, appidsOf(featured));

        return ResponseEntity.ok(new TasteResultResponse(score.code(), score.axisScores(),
                TasteViewMapper.toView(content, type, Lang.from(lang), featured)));
    }

    @Operation(summary = "More games for a taste type")
    @PostMapping("/recommend")
    public ResponseEntity<TasteRecommendResult> recommend(@Valid @RequestBody TasteRecommendRequest request) {
        // 없는 유형이면 억제 조회와 재시도 전에 404로 끝낸다
        findType(request.code());
        int review = request.review() == null ? DEFAULT_REVIEW : request.review();
        TasteRecommendResult result = withSuppressionFallback(request.clientId(), request.excludeAppids(),
                ex -> recommendService.recommend(request.code(), review, request.korean(), request.free(), ex));
        impressionService.record(request.clientId(), null, FeedbackContext.TASTE, appidsOf(result.games()));
        return ResponseEntity.ok(result);
    }

    // RecommendController와 같은 규칙, 억제가 실제로 더해졌는데 404면 억제를 떼고 한 번 재시도함
    // 기존 컨트롤러의 메서드를 공용으로 옮기면 그쪽 억제 테스트가 깨져 따로 둔다
    private TasteRecommendResult withSuppressionFallback(String clientId, Set<Long> clientExcludes,
                                                         Function<Set<Long>, TasteRecommendResult> call) {
        Set<Long> effective = suppressionService.resolveExcludes(clientId, clientExcludes);
        Set<Long> original = clientExcludes == null ? Set.of() : clientExcludes;
        boolean suppressed = effective.size() > original.size();
        try {
            return call.apply(effective);
        } catch (ResponseStatusException e) {
            if (suppressed && e.getStatusCode().value() == 404) {
                log.info("taste recommend retried without suppression: suppressedExtra={}",
                        effective.size() - original.size());
                return call.apply(original);
            }
            throw e;
        }
    }
}
