package SteamGame.recommend.domain.game.mapper;

import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.game.entity.Game;
import org.springframework.stereotype.Component;

@Component
public class GameMapper {
    private final static String STEAM_STORE_URL = "https://store.steampowered.com/app/";

    public static SteamAppDTO convertToDTO(Game game) {
        SteamAppDTO app = new SteamAppDTO();
        app.setName(game.getName());
        app.setAppid(game.getAppid());
        app.setShortDescription(game.getDescription());
        app.setHeaderImage(game.getImageUrl());
        app.setSteamStore(STEAM_STORE_URL + game.getAppid() + "?l=korean");
        return app;
    }
}
