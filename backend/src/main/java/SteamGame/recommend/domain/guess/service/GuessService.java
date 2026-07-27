package SteamGame.recommend.domain.guess.service;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.entity.Review;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.game.repository.ReviewRepository;
import SteamGame.recommend.domain.recommendation.service.InMemoryCacheService;
import SteamGame.recommend.domain.tag.repository.TagRepository;
import SteamGame.recommend.domain.guess.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GuessService {

    private final GameRepository gameRepository;
    private final ReviewRepository reviewRepository;
    private final TagRepository tagRepository;
    private final InMemoryCacheService cacheService;

    private static final int TOTAL_ROUNDS = 7;
    private static final int MIN_REVIEW_COUNT = 3500;
    private static final Duration SESSION_TTL = Duration.ofHours(2);
    private static final String SESSION_KEY_PREFIX = "review_guess:";

    public GuessService(GameRepository gameRepository,
                        ReviewRepository reviewRepository,
                        TagRepository tagRepository,
                        InMemoryCacheService cacheService) {
        this.gameRepository = gameRepository;
        this.reviewRepository = reviewRepository;
        this.tagRepository = tagRepository;
        this.cacheService = cacheService;
    }

    @Transactional(readOnly = true)
    public ReviewGuessStartResponseDTO startGame() {
        log.info("Starting review guess game");

        List<ReviewGuessRoundDTO> rounds = generateGameRounds();

        if (rounds.size() < TOTAL_ROUNDS) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Could not generate enough game rounds");
        }

        String sessionId = UUID.randomUUID().toString();
        ReviewGuessSession session = new ReviewGuessSession(
                sessionId,
                rounds,
                new ArrayList<>(),
                false,
                0
        );

        saveSession(session);

        // Remove correct answer ID when sending to client
        List<ReviewGuessRoundDTO> clientRounds = rounds.stream()
                .map(this::sanitizeRoundForClient)
                .collect(Collectors.toList());

        return new ReviewGuessStartResponseDTO(sessionId, clientRounds);
    }

    @Transactional(readOnly = true)
    public ReviewGuessAnswerResponseDTO submitAnswer(String sessionId, int roundIndex, Long selectedGameId) {
        ReviewGuessSession session = getSession(sessionId);

        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game session not found");
        }

        if (roundIndex < 0 || roundIndex >= session.getRounds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid round index");
        }

        if (session.getUserAnswers().size() != roundIndex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid round sequence");
        }

        ReviewGuessRoundDTO round = session.getRounds().get(roundIndex);
        boolean isCorrect = selectedGameId.equals(round.getCorrectAnswerId());

        // Save answer
        session.getUserAnswers().add(selectedGameId);

        if (isCorrect) {
            session.setCurrentScore(session.getCurrentScore() + 1);
        }

        // Check if game is completed
        boolean gameCompleted = session.getUserAnswers().size() == TOTAL_ROUNDS;
        session.setCompleted(gameCompleted);

        saveSession(session);

        // Find correct game name
        String correctGameName = round.getGameOptions().stream()
                .filter(game -> game.getAppid().equals(round.getCorrectAnswerId()))
                .map(ReviewGuessGameDTO::getName)
                .findFirst()
                .orElse("Unknown");

        return new ReviewGuessAnswerResponseDTO(
                isCorrect,
                gameCompleted,
                gameCompleted ? -1 : roundIndex + 1,
                round.getCorrectAnswerId(),
                correctGameName,
                session.getCurrentScore()
        );
    }

    public ReviewGuessResultDTO getGameResult(String sessionId) {
        ReviewGuessSession session = getSession(sessionId);

        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game session not found");
        }

        if (!session.isCompleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Game not completed yet");
        }

        // Generate round-by-round results
        List<ReviewGuessRoundResultDTO> roundResults = new ArrayList<>();
        for (int i = 0; i < session.getRounds().size(); i++) {
            ReviewGuessRoundDTO round = session.getRounds().get(i);
            Long userAnswer = session.getUserAnswers().get(i);

            String selectedGameName = round.getGameOptions().stream()
                    .filter(game -> game.getAppid().equals(userAnswer))
                    .map(ReviewGuessGameDTO::getName)
                    .findFirst()
                    .orElse("Unknown");

            String correctGameName = round.getGameOptions().stream()
                    .filter(game -> game.getAppid().equals(round.getCorrectAnswerId()))
                    .map(ReviewGuessGameDTO::getName)
                    .findFirst()
                    .orElse("Unknown");

            roundResults.add(new ReviewGuessRoundResultDTO(
                    i,
                    userAnswer.equals(round.getCorrectAnswerId()),
                    userAnswer,
                    selectedGameName,
                    round.getCorrectAnswerId(),
                    correctGameName
            ));
        }

        int correctAnswers = session.getCurrentScore();
        double accuracy = (double) correctAnswers / TOTAL_ROUNDS * 100;
        String grade = calculateGrade(accuracy);

        return new ReviewGuessResultDTO(
                sessionId,
                correctAnswers,
                TOTAL_ROUNDS,
                accuracy,
                roundResults,
                grade
        );
    }

    public ReviewGuessStatusDTO getGameStatus(String sessionId) {
        ReviewGuessSession session = getSession(sessionId);

        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game session not found");
        }

        return new ReviewGuessStatusDTO(
                sessionId,
                session.getUserAnswers().size(),
                TOTAL_ROUNDS,
                session.isCompleted(),
                session.getCurrentScore()
        );
    }

    // Generate 7 rounds
    private List<ReviewGuessRoundDTO> generateGameRounds() {
        List<ReviewGuessRoundDTO> rounds = new ArrayList<>();
        Set<Long> usedGameIds = new HashSet<>();

        for (int i = 0; i < TOTAL_ROUNDS; i++) {
            try {
                ReviewGuessRoundDTO round = generateSingleRound(usedGameIds);
                if (round != null) {
                    rounds.add(round);
                    // Record used games
                    round.getGameOptions().forEach(game -> usedGameIds.add(game.getAppid()));
                }
            } catch (Exception e) {
                log.warn("Failed to generate round {}: {}", i, e.getMessage());
            }
        }

        return rounds;
    }

    // Generate single round
    private ReviewGuessRoundDTO generateSingleRound(Set<Long> usedGameIds) {
        // 1. Select correct game (games with 3500+ reviews, not yet used)
        Game correctGame = selectCorrectGame(usedGameIds);
        if (correctGame == null) {
            return null;
        }

        // 2. Get reviews for the correct game
        Review correctReview = reviewRepository.findByAppidIn(List.of(correctGame.getAppid()))
                .stream().findFirst().orElse(null);

        if (correctReview == null ||
                correctReview.getPositiveReviewText() == null ||
                correctReview.getNegativeReviewText() == null) {
            return null;
        }

        // 3. Select 2 games with similar tags
        List<Game> similarGames = selectSimilarGames(correctGame, usedGameIds);
        if (similarGames.size() < 2) {
            return null;
        }

        // 4. Create game options (correct answer + 2 similar games)
        List<ReviewGuessGameDTO> gameOptions = new ArrayList<>();
        gameOptions.add(convertToGameDTO(correctGame));
        similarGames.forEach(game -> gameOptions.add(convertToGameDTO(game)));

        // Shuffle order
        Collections.shuffle(gameOptions);

        return new ReviewGuessRoundDTO(
                0, // roundIndex will be set later
                correctReview.getPositiveReviewText(),
                correctReview.getNegativeReviewText(),
                gameOptions,
                correctGame.getAppid()
        );
    }

    // Select correct game (games with 3500+ reviews)
    private Game selectCorrectGame(Set<Long> usedGameIds) {
        List<Game> candidateGames = gameRepository.findRandomGamesWithoutTags(MIN_REVIEW_COUNT, null, null);

        return candidateGames.stream()
                .filter(game -> !usedGameIds.contains(game.getAppid()))
                .findFirst()
                .orElse(null);
    }

    // Select games with similar tags
    private List<Game> selectSimilarGames(Game correctGame, Set<Long> usedGameIds) {
        // Get tags of the correct game
        List<String> correctGameTags = tagRepository.findTagNamesByAppIds(List.of(correctGame.getAppid()));

        if (correctGameTags.isEmpty()) {
            // If no tags, select randomly
            return gameRepository.findRandomGamesWithoutTags(MIN_REVIEW_COUNT, null, null)
                    .stream()
                    .filter(game -> !usedGameIds.contains(game.getAppid()) &&
                            !game.getAppid().equals(correctGame.getAppid()))
                    .limit(2)
                    .collect(Collectors.toList());
        }

        // Find similar games based on tags (use up to 3 tags)
        List<String> tagsToUse = correctGameTags.stream()
                .limit(3)
                .collect(Collectors.toList());

        List<Game> similarGames = gameRepository.findRandomGameByTags(
                tagsToUse,
                Math.min(tagsToUse.size(), 2), // Minimum 2 tags match
                MIN_REVIEW_COUNT,
                null,
                null,
                false,
                Collections.emptyList()
        );

        return similarGames.stream()
                .filter(game -> !usedGameIds.contains(game.getAppid()) &&
                        !game.getAppid().equals(correctGame.getAppid()))
                .limit(2)
                .collect(Collectors.toList());
    }

    private ReviewGuessGameDTO convertToGameDTO(Game game) {
        return new ReviewGuessGameDTO(
                game.getAppid(),
                game.getName(),
                game.getImageUrl(),
                game.getDescription()
        );
    }

    // Remove correct answer ID for client
    private ReviewGuessRoundDTO sanitizeRoundForClient(ReviewGuessRoundDTO round) {
        ReviewGuessRoundDTO clientRound = new ReviewGuessRoundDTO();
        clientRound.setRoundIndex(round.getRoundIndex());
        clientRound.setPositiveReview(round.getPositiveReview());
        clientRound.setNegativeReview(round.getNegativeReview());
        clientRound.setGameOptions(round.getGameOptions());
        // Do not set correctAnswerId
        return clientRound;
    }

    private String calculateGrade(double accuracy) {
        if (accuracy >= 90) return "S";
        if (accuracy >= 80) return "A";
        if (accuracy >= 70) return "B";
        if (accuracy >= 60) return "C";
        return "D";
    }

    // Redis session save/load
    private void saveSession(ReviewGuessSession session) {
        String key = SESSION_KEY_PREFIX + session.getSessionId();
        cacheService.setObject(key, session, SESSION_TTL);
    }

    private ReviewGuessSession getSession(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        return cacheService.getObject(key, ReviewGuessSession.class).orElse(null);
    }
}