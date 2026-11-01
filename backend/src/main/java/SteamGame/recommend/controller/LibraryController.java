package SteamGame.recommend.controller;

import SteamGame.recommend.domain.event.entity.FunnelEventType;
import SteamGame.recommend.domain.event.service.FunnelEventService;
import SteamGame.recommend.domain.library.LibraryAnalysisService;
import SteamGame.recommend.domain.library.content.LibraryContentLoader;
import SteamGame.recommend.domain.library.dto.AnalyzeRequest;
import SteamGame.recommend.domain.library.dto.AnalyzeResponse;
import SteamGame.recommend.domain.library.dto.LibraryViewMapper;
import SteamGame.recommend.domain.library.dto.ShareRequest;
import SteamGame.recommend.domain.library.dto.ShareResponse;
import SteamGame.recommend.domain.library.dto.SharedAnalysisResponse;
import SteamGame.recommend.domain.taste.content.Lang;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "library", description = "Steam library analysis API")
@RestController
@RequestMapping("/api/library")
public class LibraryController {

    private final LibraryAnalysisService service;
    private final LibraryContentLoader contentLoader;
    private final FunnelEventService eventService;

    public LibraryController(LibraryAnalysisService service, LibraryContentLoader contentLoader,
                             FunnelEventService eventService) {
        this.service = service;
        this.contentLoader = contentLoader;
        this.eventService = eventService;
    }

    @Operation(summary = "Analyze a Steam library")
    @PostMapping("/analyze")
    public ResponseEntity<AnalyzeResponse> analyze(@Valid @RequestBody AnalyzeRequest request,
                                                   @RequestParam(required = false) String lang) {
        LibraryAnalysisService.Analyzed result = service.analyze(request.steamId());
        AnalyzeResponse body = LibraryViewMapper.toAnalyzeResponse(result.analysisId(), result.profile(),
                result.analysis(), contentLoader.content(), Lang.from(lang));
        // 응답을 다 만든 뒤에 완료 이벤트를 남긴다, clientId가 있을 때만 남긴다
        if (request.clientId() != null) {
            eventService.record(request.clientId(), FunnelEventType.LIBRARY_COMPLETE, null, null);
        }
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Save an analysis as a shareable snapshot")
    @PostMapping("/share")
    public ResponseEntity<ShareResponse> share(@Valid @RequestBody ShareRequest request) {
        return ResponseEntity.ok(new ShareResponse(service.share(request.analysisId())));
    }

    @Operation(summary = "Read a shared analysis")
    @GetMapping("/share/{shareId}")
    public ResponseEntity<SharedAnalysisResponse> shared(@PathVariable String shareId,
                                                         @RequestParam(required = false) String lang) {
        return ResponseEntity.ok(service.renderShared(shareId, Lang.from(lang)));
    }
}
