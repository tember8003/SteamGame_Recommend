package SteamGame.recommend.controller;

import SteamGame.recommend.domain.game.dto.GameNameRequestDTO;
import SteamGame.recommend.domain.game.dto.GameRequestDTO;
import SteamGame.recommend.domain.game.dto.SteamIdRequestDTO;
import SteamGame.recommend.domain.game.dto.TextRequestDTO;
import SteamGame.recommend.domain.recommendation.dto.SteamDTO;

import SteamGame.recommend.domain.recommendation.service.RecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name="recommend", description="게임 추천 API")
@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @Operation(summary="태그 기반 랜덤 추천")
    @ApiResponse(responseCode="200", description="성공")
    @PostMapping("/random")
    public List<SteamDTO.SteamApp> randomGame(@RequestBody GameRequestDTO request) {
        Boolean excluded_check;

        if(request.getExcludedTag()==null){
            excluded_check=false;
        }
        else{
            excluded_check=true;
        }

        return recommendService.findGame(request.getTags(),request.getReview(),request.getKorean_check(),request.getFree_check(),excluded_check,request.getExcludedTag());

    }

    @Operation(summary="직접 입력 태그 추천")
    @ApiResponse(responseCode="200", description="성공")
    @PostMapping("/input")
    public SteamDTO.RecommendationResult inputRandomGame(
            @RequestBody TextRequestDTO dto) {
        return recommendService.selectInfo(dto.getText(),dto.getReview(),dto.getKorean_check(),dto.getFree_check());
    }

    @Operation(summary="내 프로필 게임 기반 추천")
    @ApiResponse(responseCode="200", description="성공")
    @PostMapping("/profile")
    public SteamDTO.RecommendationResult randomGameByProfile(@RequestBody SteamIdRequestDTO dto) {
        return recommendService.recommendByProfile(dto.getSteamId(),dto.getReview(),dto.getKorean_check(),dto.getFree_check());
    }

    @Operation(summary="최근 2주간 플레이 기준 추천")
    @ApiResponse(responseCode="200", description="성공")
    @PostMapping("/RecentPlay")
    public SteamDTO.RecommendationResult randomGameByRecentPlay(@RequestBody SteamIdRequestDTO dto){
        return recommendService.recommendByRecentPlay(dto.getSteamId(),dto.getReview(),dto.getKorean_check(),dto.getFree_check());
    }

    @Operation(summary="게임 하나 고르면 비슷한 태그의 게임들 추천")
    @ApiResponse(responseCode="200", description="성공")
    @PostMapping("/similar")
    public SteamDTO.RecommendationResult randomGameBySimilarGame(@RequestBody GameNameRequestDTO dto){
        return recommendService.recommendBySimilarGame(dto.getGameName(),dto.getReview(),dto.getKorean_check(),dto.getFree_check());
    }
}