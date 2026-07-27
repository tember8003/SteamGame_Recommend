package SteamGame.recommend.infra.crawler.dto;

public record GameSeed(
        long appid,
        String name,
        String developer,
        String publisher,
        Long ownersEstimate,
        int reviewCount
) {}
