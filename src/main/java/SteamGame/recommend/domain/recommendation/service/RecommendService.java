package SteamGame.recommend.domain.recommendation.service;

import SteamGame.recommend.domain.recommendation.dto.SteamDTO;

import java.util.List;

public interface RecommendService {
    // 태그, 리뷰, 한글화, 무료여부 등 조건으로 게임 찾기.
    List<SteamDTO.SteamApp> findGame(String[] tags, int review, Boolean koreanCheck, Boolean freeCheck,Boolean excluded_check,String[] excludedTag);

    // Gemini API를 활용해 게임 태그 추출 후 추천
    SteamDTO.RecommendationResult selectInfo(String input,int review, Boolean koreanCheck, Boolean freeCheck);

    // 스팀 사용자 프로필 기반 추천
    SteamDTO.RecommendationResult recommendByProfile(String steamId,int review, Boolean koreanCheck, Boolean freeCheck);

    // 최근 플레이(2주) 기반 추천
    SteamDTO.RecommendationResult recommendByRecentPlay(String steamId,int review, Boolean koreanCheck, Boolean freeCheck);

    //사용자 프로필에서 뽑아낸 태그들을 바탕으로 게임 찾기
    List<SteamDTO.SteamApp> recommendWithCooccurrence(List<String> topTags,int review, Boolean koreanCheck, Boolean freeCheck);

    //원하는 게임과 비슷한 게임 찾기
    SteamDTO.RecommendationResult recommendBySimilarGame(String game,int review, Boolean koreanCheck, Boolean freeCheck);

    // 전체 태그 목록 반환
    List<String> getTags();

    SteamDTO.SteamApp findGameByAppid(long appid);
}