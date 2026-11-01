package SteamGame.recommend.domain.taste.service;

import SteamGame.recommend.domain.recommendation.dto.ExplanationItem;

import java.util.List;

// 추천 게임 하나에 붙는 추천 이유 목록
public record GameExplanation(long appid, List<ExplanationItem> items) {
}
