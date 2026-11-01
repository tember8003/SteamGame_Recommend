package SteamGame.recommend.controller;

import SteamGame.recommend.domain.event.dto.EventRequest;
import SteamGame.recommend.domain.event.service.FunnelEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "events", description = "Funnel event API")
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final FunnelEventService eventService;

    public EventController(FunnelEventService eventService) {
        this.eventService = eventService;
    }

    // 허용 조합이 아니면 validate가 400을 던져 저장하지 않는다, 저장은 비동기라 바로 202를 돌려준다
    @Operation(summary = "Record a funnel event")
    @PostMapping
    public ResponseEntity<Void> record(@Valid @RequestBody EventRequest request) {
        eventService.validate(request.type(), request.ref(), request.channel());
        eventService.record(request.clientId(), request.type(), request.ref(), request.channel());
        return ResponseEntity.accepted().build();
    }
}
