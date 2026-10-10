package SteamGame.recommend.domain.recommendation.similar;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.impression.entity.ImpressionSource;

// 섞은 결과 한 칸, 그 게임을 고른 순위기를 함께 남긴다
public record InterleavedPick(Game game, ImpressionSource source) {
}
