package SteamGame.recommend.domain.recommendation.service;

import SteamGame.recommend.domain.recommendation.dto.RecommendationResultDTO;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;

import java.util.List;

public interface RecommendService {
    // Find games based on tags, review count, Korean language support, free status, etc.
    RecommendationResultDTO findGame(String[] tags, int review, Boolean koreanCheck, Boolean freeCheck, Boolean excluded_check, String[] excludedTag);

    // Extract game tags using Gemini API and provide recommendations
    RecommendationResultDTO selectInfo(String input, int review, Boolean koreanCheck, Boolean freeCheck);

    // Recommend based on Steam user profile
    RecommendationResultDTO recommendByProfile(String steamId, int review, Boolean koreanCheck, Boolean freeCheck);

    // Recommend based on recently played games (past 2 weeks)
    RecommendationResultDTO recommendByRecentPlay(String steamId, int review, Boolean koreanCheck, Boolean freeCheck);

    // Find games based on tags extracted from the user's profile
    List<SteamAppDTO> recommendWithCooccurrence(List<String> topTags, int review, Boolean koreanCheck, Boolean freeCheck);

    // Find games similar to the specified game
    RecommendationResultDTO recommendBySimilarGame(String game, int review, Boolean koreanCheck, Boolean freeCheck);
}