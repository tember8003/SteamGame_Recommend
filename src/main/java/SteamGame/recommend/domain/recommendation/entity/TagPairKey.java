package SteamGame.recommend.domain.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
public class TagPairKey implements Serializable {
    @Column(name = "tag1")
    private String firstTag;

    @Column(name = "tag2")
    private String secondTag;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TagPairKey)) return false;
        TagPairKey tagPairKey = (TagPairKey) o;
        return Objects.equals(firstTag, tagPairKey.firstTag) &&
                Objects.equals(secondTag, tagPairKey.secondTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstTag, secondTag);
    }
}
