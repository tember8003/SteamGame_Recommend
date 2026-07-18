package SteamGame.recommend.infra.crawler.dto;

import java.time.LocalDate;

public record StorefrontDetail(
        long appid, String name, String description, String imageUrl,
        boolean isFree, boolean koreanSupport, String genres, Integer price,
        LocalDate releaseDate, String developer, String publisher) {}
