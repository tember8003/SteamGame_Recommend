package SteamGame.recommend.domain.guess.service;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.entity.Review;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.game.repository.ReviewRepository;
import SteamGame.recommend.domain.guess.dto.ReviewGuessGameDTO;
import SteamGame.recommend.domain.guess.dto.ReviewGuessRoundDTO;
import SteamGame.recommend.domain.guess.dto.ReviewGuessStartResponseDTO;
import SteamGame.recommend.domain.recommendation.service.InMemoryCacheService;
import SteamGame.recommend.domain.tag.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuessServiceTest {

    @Mock GameRepository gameRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock TagRepository tagRepository;
    @Mock InMemoryCacheService cacheService;
    @InjectMocks GuessService service;

    private static Game game(long appid, String imageUrl) {
        Game g = new Game();
        g.setAppid(appid);
        g.setName("Game " + appid);
        g.setImageUrl(imageUrl);
        return g;
    }

    private static List<Game> games(long fromInclusive, long toInclusive, boolean everyOtherWithoutImage) {
        List<Game> list = new ArrayList<>();
        for (long id = fromInclusive; id <= toInclusive; id++) {
            boolean noImage = everyOtherWithoutImage && id % 2 == 0;
            list.add(game(id, noImage ? null : "https://img/" + id + ".jpg"));
        }
        return list;
    }

    private static Review review(long appid, String positive, String negative) {
        Review r = new Review();
        r.setAppid(appid);
        r.setPositiveReviewText(positive);
        r.setNegativeReviewText(negative);
        return r;
    }

    // 보기 후보(비슷한 게임) 풀을 태그 조회 결과로 고정한다
    private void stubSimilarPool(List<Game> pool) {
        lenient().when(tagRepository.findTagNamesByAppIds(anyList())).thenReturn(List.of("Action"));
        lenient().when(gameRepository.findRandomGameByTags(anyList(), anyLong(), anyInt(), any(), any(), any(), anyList()))
                .thenReturn(pool);
    }

    // 후보 리뷰를 한 번에 조회하므로 요청된 appid 전부에 대해 리뷰를 돌려준다
    @SuppressWarnings("unchecked")
    private void stubReviews(long appidWithoutNegative) {
        when(reviewRepository.findByAppidIn(anyList())).thenAnswer(inv -> ((List<Long>) inv.getArgument(0)).stream()
                .map(id -> id == appidWithoutNegative ? review(id, "good", null) : review(id, "good", "bad"))
                .toList());
    }

    @Test
    void startGame_skipsCandidateWithoutNegativeReview_andStillBuildsSevenRounds() {
        when(gameRepository.findReviewGuessCandidates(anyInt())).thenReturn(games(1, 10, false));
        stubReviews(1L);
        stubSimilarPool(games(100, 140, false));

        ReviewGuessStartResponseDTO res = service.startGame();

        assertThat(res.getRounds()).hasSize(7);
        assertThat(res.getRounds())
                .flatExtracting(ReviewGuessRoundDTO::getGameOptions)
                .extracting(ReviewGuessGameDTO::getAppid)
                .doesNotContain(1L);
    }

    @Test
    void startGame_neverUsesOptionWithoutImage() {
        when(gameRepository.findReviewGuessCandidates(anyInt())).thenReturn(games(1, 10, false));
        stubReviews(-1L);
        stubSimilarPool(games(100, 160, true));

        ReviewGuessStartResponseDTO res = service.startGame();

        assertThat(res.getRounds()).hasSize(7);
        assertThat(res.getRounds())
                .flatExtracting(ReviewGuessRoundDTO::getGameOptions)
                .extracting(ReviewGuessGameDTO::getImageUrl)
                .doesNotContainNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void startGame_skipsCandidateWhoseSimilarPoolIsTooSmall() {
        when(gameRepository.findReviewGuessCandidates(anyInt())).thenReturn(games(1, 10, false));
        stubReviews(-1L);
        // appid 1 만 드문 태그라 보기 후보가 비고, 나머지는 넉넉한 풀을 받는다
        when(tagRepository.findTagNamesByAppIds(anyList())).thenAnswer(inv ->
                ((List<Long>) inv.getArgument(0)).get(0) == 1L ? List.of("Rare") : List.of("Action"));
        List<Game> pool = games(100, 140, false);
        when(gameRepository.findRandomGameByTags(anyList(), anyLong(), anyInt(), any(), any(), any(), anyList()))
                .thenAnswer(inv -> ((List<String>) inv.getArgument(0)).contains("Rare") ? List.of() : pool);

        ReviewGuessStartResponseDTO res = service.startGame();

        assertThat(res.getRounds()).hasSize(7);
        assertThat(res.getRounds())
                .flatExtracting(ReviewGuessRoundDTO::getGameOptions)
                .extracting(ReviewGuessGameDTO::getAppid)
                .doesNotContain(1L);
    }

    @Test
    void startGame_withoutCandidates_throwsNotFound() {
        when(gameRepository.findReviewGuessCandidates(anyInt())).thenReturn(List.of());

        assertThatThrownBy(() -> service.startGame())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
