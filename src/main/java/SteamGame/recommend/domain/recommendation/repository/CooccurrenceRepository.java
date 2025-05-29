package SteamGame.recommend.domain.recommendation.repository;

import SteamGame.recommend.domain.recommendation.entity.TagCooccurrence;
import SteamGame.recommend.domain.recommendation.entity.TagPairKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CooccurrenceRepository extends JpaRepository<TagCooccurrence, TagPairKey> {
}
