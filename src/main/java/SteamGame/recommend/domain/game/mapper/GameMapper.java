package SteamGame.recommend.domain.game.mapper;

import SteamGame.recommend.domain.recommendation.dto.SteamDTO;
import SteamGame.recommend.domain.game.entity.Game;
import org.springframework.stereotype.Component;

@Component
public class GameMapper {
    private final static String STEAM_STORE_URL = "https://store.steampowered.com/app/";

    public static SteamDTO.SteamApp convertToDTO(Game game) {
        SteamDTO.SteamApp app = new SteamDTO.SteamApp();
        app.setName(game.getName());
        app.setAppid(game.getAppid());
        app.setShortDescription(game.getDescription());
        app.setHeaderImage(game.getImageUrl());
        app.setSteamStore(STEAM_STORE_URL + game.getAppid() + "?l=korean");
        return app;
    }
}
