package SteamGame.recommend.domain.recommendation.service;

import SteamGame.recommend.domain.recommendation.entity.TagCooccurrence;
import SteamGame.recommend.domain.recommendation.entity.TagPairKey;
import SteamGame.recommend.domain.recommendation.repository.CooccurrenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class CooccurrenceService {
    private final CooccurrenceRepository repo;

    public CooccurrenceService(CooccurrenceRepository repo) {
        this.repo = repo;
    }

    //전체 엔티티 반환
    public Optional<TagCooccurrence> findOptimalPairEntity(List<String> tags, int threshold) {
        for (int i = 0; i < tags.size(); i++) {
            for (int j = i + 1; j < tags.size(); j++) {
                String t1 = tags.get(i), t2 = tags.get(j);

                //단어순 정렬
                TagPairKey key = new TagPairKey(
                        t1.compareTo(t2) < 0 ? t1 : t2,
                        t1.compareTo(t2) < 0 ? t2 : t1
                );

                Optional<TagCooccurrence> opt = repo.findById(key);
                if (opt.isPresent() && opt.get().getCount() >= threshold) {
                    return opt;
                }
            }
        }
        return Optional.empty();
    }

    public Optional<TagPairKey> findOptimalPairKey(List<String> tags, int threshold) {
        return findOptimalPairEntity(tags, threshold)
                .map(TagCooccurrence::getId);
    }
}
