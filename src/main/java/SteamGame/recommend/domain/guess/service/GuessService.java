package SteamGame.recommend.domain.guess.service;

import SteamGame.recommend.domain.game.entity.Game;
import SteamGame.recommend.domain.game.entity.Review;
import SteamGame.recommend.domain.game.repository.GameRepository;
import SteamGame.recommend.domain.game.repository.ReviewRepository;
import SteamGame.recommend.domain.tag.repository.TagRepository;
import SteamGame.recommend.domain.guess.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final int TOTAL_ROUNDS = 7;
    private static final int MIN_REVIEW_COUNT = 5000;
    private static final Duration SESSION_TTL = Duration.ofHours(2);
    private static final String SESSION_KEY_PREFIX = "review_guess:";

    public GuessService(GameRepository gameRepository,
                              ReviewRepository reviewRepository,
                              TagRepository tagRepository,
                              RedisTemplate<String, String> redisTemplate,
                              ObjectMapper objectMapper) {
        this.gameRepository = gameRepository;
        this.reviewRepository = reviewRepository;
        this.tagRepository = tagRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
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

        // 클라이언트로 보낼 때는 정답 ID 제거
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

        // 답안 저장
        session.getUserAnswers().add(selectedGameId);

        if (isCorrect) {
            session.setCurrentScore(session.getCurrentScore() + 1);
        }

        // 게임 완료 여부 확인
        boolean gameCompleted = session.getUserAnswers().size() == TOTAL_ROUNDS;
        session.setCompleted(gameCompleted);

        saveSession(session);

        // 정답 게임 이름 찾기
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

        // 라운드별 결과 생성
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

    // 7라운드 생성
    private List<ReviewGuessRoundDTO> generateGameRounds() {
        List<ReviewGuessRoundDTO> rounds = new ArrayList<>();
        Set<Long> usedGameIds = new HashSet<>();

        for (int i = 0; i < TOTAL_ROUNDS; i++) {
            try {
                ReviewGuessRoundDTO round = generateSingleRound(usedGameIds);
                if (round != null) {
                    rounds.add(round);
                    // 사용된 게임들을 기록
                    round.getGameOptions().forEach(game -> usedGameIds.add(game.getAppid()));
                }
            } catch (Exception e) {
                log.warn("Failed to generate round {}: {}", i, e.getMessage());
            }
        }

        return rounds;
    }

    // 단일 라운드 생성
    private ReviewGuessRoundDTO generateSingleRound(Set<Long> usedGameIds) {
        // 1. 정답 게임 선택 (리뷰 5000개 이상, 아직 사용되지 않은 게임)
        Game correctGame = selectCorrectGame(usedGameIds);
        if (correctGame == null) {
            return null;
        }

        // 2. 정답 게임의 리뷰 가져오기
        Review correctReview = reviewRepository.findByAppidIn(List.of(correctGame.getAppid()))
                .stream().findFirst().orElse(null);

        if (correctReview == null ||
                correctReview.getPositiveReviewText() == null ||
                correctReview.getNegativeReviewText() == null) {
            return null;
        }

        // 3. 비슷한 태그를 가진 게임 2개 선택
        List<Game> similarGames = selectSimilarGames(correctGame, usedGameIds);
        if (similarGames.size() < 2) {
            return null;
        }

        // 4. 게임 옵션 생성 (정답 + 2개 비슷한 게임)
        List<ReviewGuessGameDTO> gameOptions = new ArrayList<>();
        gameOptions.add(convertToGameDTO(correctGame));
        similarGames.forEach(game -> gameOptions.add(convertToGameDTO(game)));

        // 순서 섞기
        Collections.shuffle(gameOptions);

        return new ReviewGuessRoundDTO(
                0, // roundIndex는 나중에 설정
                correctReview.getPositiveReviewText(),
                correctReview.getNegativeReviewText(),
                gameOptions,
                correctGame.getAppid()
        );
    }

    // 정답 게임 선택 (리뷰 5000개 이상)
    private Game selectCorrectGame(Set<Long> usedGameIds) {
        List<Game> candidateGames = gameRepository.findRandomGamesWithoutTags(MIN_REVIEW_COUNT, null, null);

        return candidateGames.stream()
                .filter(game -> !usedGameIds.contains(game.getAppid()))
                .findFirst()
                .orElse(null);
    }

    // 비슷한 태그를 가진 게임들 선택
    private List<Game> selectSimilarGames(Game correctGame, Set<Long> usedGameIds) {
        // 정답 게임의 태그들 가져오기
        List<String> correctGameTags = tagRepository.findTagNamesByAppIds(List.of(correctGame.getAppid()));

        if (correctGameTags.isEmpty()) {
            // 태그가 없으면 랜덤으로 선택
            return gameRepository.findRandomGamesWithoutTags(MIN_REVIEW_COUNT, null, null)
                    .stream()
                    .filter(game -> !usedGameIds.contains(game.getAppid()) &&
                            !game.getAppid().equals(correctGame.getAppid()))
                    .limit(2)
                    .collect(Collectors.toList());
        }

        // 태그 기반으로 비슷한 게임 찾기 (최대 3개 태그 사용)
        List<String> tagsToUse = correctGameTags.stream()
                .limit(3)
                .collect(Collectors.toList());

        List<Game> similarGames = gameRepository.findRandomGameByTags(
                tagsToUse,
                Math.min(tagsToUse.size(), 2), // 최소 2개 태그 일치
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

    // 클라이언트용으로 정답 ID 제거
    private ReviewGuessRoundDTO sanitizeRoundForClient(ReviewGuessRoundDTO round) {
        ReviewGuessRoundDTO clientRound = new ReviewGuessRoundDTO();
        clientRound.setRoundIndex(round.getRoundIndex());
        clientRound.setPositiveReview(round.getPositiveReview());
        clientRound.setNegativeReview(round.getNegativeReview());
        clientRound.setGameOptions(round.getGameOptions());
        // correctAnswerId는 설정하지 않음
        return clientRound;
    }

    private String calculateGrade(double accuracy) {
        if (accuracy >= 90) return "S";
        if (accuracy >= 80) return "A";
        if (accuracy >= 70) return "B";
        if (accuracy >= 60) return "C";
        return "D";
    }

    // Redis 세션 저장/로드
    private void saveSession(ReviewGuessSession session) {
        try {
            String key = SESSION_KEY_PREFIX + session.getSessionId();
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(key, json, SESSION_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize review guess session: {}", session.getSessionId(), e);
            throw new RuntimeException("Failed to save game session", e);
        }
    }

    private ReviewGuessSession getSession(String sessionId) {
        try {
            String key = SESSION_KEY_PREFIX + sessionId;
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, ReviewGuessSession.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize review guess session: {}", sessionId, e);
            return null;
        }
    }
}