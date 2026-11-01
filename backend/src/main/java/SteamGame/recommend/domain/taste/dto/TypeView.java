package SteamGame.recommend.domain.taste.dto;

import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.taste.content.TagWeight;

import java.util.List;

// name은 캐릭터 이름, work는 작품 이름
public record TypeView(
        String code,
        String name,
        String work,
        long workAppid,
        String oneLiner,
        String description,
        String traits,
        List<AxisView> axes,
        List<TagWeight> tags,
        List<SteamAppDTO> featuredGames,
        ShareView share) {
}
