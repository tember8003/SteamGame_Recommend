package SteamGame.recommend.infra.crawler.dto;

import java.math.BigDecimal;

public record ReviewDetail(
        long appid, String reviewScoreDesc, int totalReviews,
        BigDecimal positivePercentage, String positiveText, String negativeText) {}
