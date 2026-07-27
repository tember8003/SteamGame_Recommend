package SteamGame.recommend.infra.crawler.dto;

import java.math.BigDecimal;

public record ReviewSeed(
        long appid,
        int totalReviews,
        BigDecimal positivePercentage
) {}
