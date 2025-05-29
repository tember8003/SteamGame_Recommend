package SteamGame.recommend.domain.game.entity;

import SteamGame.recommend.domain.tag.entity.Tag;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "game_tags")
@IdClass(GameTagId.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class GameTag {
    @Id
    @ManyToOne
    @JoinColumn(name="game_id",nullable = false)
    private Game game;

    @Id
    @ManyToOne
    @JoinColumn(name="tag_id",nullable = false)
    private Tag tag;
}
