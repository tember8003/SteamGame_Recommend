package SteamGame.recommend.domain.recommendation.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Table(name="tag_cooccurrence")
public class TagCooccurrence {
    @EmbeddedId
    private TagPairKey id;
    private int count;

}
