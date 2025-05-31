package SteamGame.recommend.domain.game.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "review_count")
    private int reviewCount;

    @Column(name = "korean_support")
    private boolean koreanSupport;

    @Column(name = "is_free")
    private boolean isFree;
}
