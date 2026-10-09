package SteamGame.recommend.domain.game.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "games")
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class Game {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long appid;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "korean_support")
    private boolean koreanSupport;

    @Column(name = "is_free")
    private boolean isFree;

    @Column(name = "review_count")
    private Integer reviewCount;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "genres", length = 500)
    private String genres;

    @Column(name = "price")
    private Integer price;

    @Column(name = "developer")
    private String developer;

    @Column(name = "publisher")
    private String publisher;

    @Column(name = "last_crawled_at")
    private OffsetDateTime lastCrawledAt;
}
