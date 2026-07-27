package SteamGame.recommend.domain.recommendation.service;

import org.junit.jupiter.api.Test;
import SteamGame.recommend.domain.recommendation.entity.TagCooccurrence;
import SteamGame.recommend.domain.recommendation.entity.TagPairKey;
import SteamGame.recommend.domain.recommendation.repository.CooccurrenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CooccurrenceServiceTest {

    @Mock
    private CooccurrenceRepository repo;

    @InjectMocks
    private CooccurrenceService service;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void findOptimalPairEntity_shouldReturnFirstPairAboveThreshold() {
        // given
        List<String> tags = List.of("A", "B", "C");
        TagPairKey keyAB = new TagPairKey("A", "B");
        TagCooccurrence coAB = new TagCooccurrence(keyAB, 10);

        // AB 태그  - 5
        when(repo.findById(keyAB)).thenReturn(Optional.of(coAB));
        // BC 태그는 없다
        when(repo.findById(new TagPairKey("B", "C"))).thenReturn(Optional.empty());

        // when
        Optional<TagCooccurrence> result = service.findOptimalPairEntity(tags, 5);

        // then
        assertTrue(result.isPresent());
        assertEquals(coAB, result.get());
        verify(repo).findById(keyAB);  // keyAB 호출 확인
    }

    @Test
    void findOptimalPairEntity_shouldReturnEmptyIfNoneAboveThreshold() {
        // given
        List<String> tags = List.of("A", "B");
        TagPairKey keyAB = new TagPairKey("A", "B");

        // 카운트 1이면 없음.
        TagCooccurrence coLow = new TagCooccurrence(keyAB, 1);
        when(repo.findById(keyAB)).thenReturn(Optional.of(coLow));

        // when
        Optional<TagCooccurrence> result = service.findOptimalPairEntity(tags, 5);

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void findOptimalPairKey_shouldExtractKeyFromEntity() {
        // given
        List<String> tags = List.of("X","Y");
        TagPairKey keyXY = new TagPairKey("X","Y");
        TagCooccurrence coXY = new TagCooccurrence(keyXY, 20);
        when(repo.findById(keyXY)).thenReturn(Optional.of(coXY));

        // when
        Optional<TagPairKey> keyResult = service.findOptimalPairKey(tags, 10);

        // then
        assertTrue(keyResult.isPresent());
        assertEquals(keyXY, keyResult.get());
    }
}
