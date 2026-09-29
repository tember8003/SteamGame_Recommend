package SteamGame.recommend.infra.crawler.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StoreItem(
        long appid, String name, String description, String imageUrl,
        boolean isFree, Boolean koreanSupport, String genres, Integer price,
        LocalDate releaseDate, String developer, String publisher,
        int reviewCount, BigDecimal positivePercentage, String reviewScore,
        List<AppTag> tags) {

    // koreanSupport 가 null 이면 응답에 언어 목록이 없었다는 뜻이라 저장소가 기존 값을 지킴
    public StoreItem {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
