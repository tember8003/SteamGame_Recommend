package SteamGame.recommend.controller;

import SteamGame.recommend.domain.game.dto.GameSearchResponseDTO;
import SteamGame.recommend.domain.game.service.GameService;
import SteamGame.recommend.domain.recommendation.dto.SteamDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@Tag(name="game", description="게임 관련 API")
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService){
        this.gameService = gameService;
    }

    @Operation(summary = "AppId로 게임 조회하기")
    @ApiResponse(responseCode="200", description="성공")
    @GetMapping("/appid")
    public SteamDTO.SteamApp getGameByAppid(@RequestParam long appid){
        return gameService.findGameByAppid(appid);
    }

    @Operation(summary="모든 태그들 리스트 반환")
    @ApiResponse(responseCode="200", description="성공")
    @GetMapping("/tags")
    public List<String> getTags(){
        return gameService.getTags();
    }

    @Operation(summary="게임 이름으로 게임 찾기")
    @ApiResponse(responseCode="200", description="성공")
    @GetMapping("/search")
    public List<GameSearchResponseDTO> searchGameNames(@RequestParam("query") String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return gameService.searchNames(query.trim());
    }

}
