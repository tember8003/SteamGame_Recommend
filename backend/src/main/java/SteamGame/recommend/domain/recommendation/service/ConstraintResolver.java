package SteamGame.recommend.domain.recommendation.service;

import SteamGame.recommend.domain.recommendation.dto.MatchRequestDTO;
import SteamGame.recommend.domain.recommendation.dto.ParsedConstraints;
import SteamGame.recommend.domain.recommendation.dto.RecommendationCriteria;
import SteamGame.recommend.domain.recommendation.dto.SessionLength;
import SteamGame.recommend.infra.api.GeminiConstraintService;
import SteamGame.recommend.infra.utils.EncryptUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

// 폼 값과 자연어 파싱 결과를 하나의 결정적 제약으로 합친다, 폼 값이 항상 자연어보다 우선함
@Service
public class ConstraintResolver {

    private final GeminiConstraintService parser;
    private final CacheService cache;
    private final ObjectMapper objectMapper;

    public ConstraintResolver(GeminiConstraintService parser, CacheService cache, ObjectMapper objectMapper) {
        this.parser = parser;
        this.cache = cache;
        this.objectMapper = objectMapper;
    }

    public RecommendationCriteria resolve(MatchRequestDTO req) {
        ParsedConstraints parsed = null;
        boolean nlParseFailed = false;

        String rawText = req.getRawText();
        // rawText 가 비어 있으면 파서를 호출하지 않음
        if (rawText != null && !rawText.isBlank()) {
            String hash = null;
            try {
                hash = EncryptUtils.sha256(rawText);
                String cached = cache.getCachedConstraints(hash);
                if (cached != null) {
                    parsed = objectMapper.readValue(cached, ParsedConstraints.class);
                }
            } catch (Exception e) {
                // 해시,캐시 조회,역직렬화 중 어떤 실패든 미스로 취급해 파서로 자가치유함
                parsed = null;
            }
            if (parsed == null) {
                parsed = parser.parseConstraints(rawText);
                if (parsed == null) {
                    nlParseFailed = true;
                } else if (hash != null) {
                    try {
                        cache.cacheConstraints(hash, objectMapper.writeValueAsString(parsed));
                    } catch (Exception e) {
                        // 캐시 저장 실패는 조용히 무시함, 파싱 결과 사용에는 지장 없음
                    }
                }
            }
        }

        Integer players = req.getPlayers() != null ? req.getPlayers() : parsedPlayers(parsed);
        SessionLength sessionLength = req.getSessionLength() != null ? req.getSessionLength() : parsedSessionLength(parsed);
        Integer budgetUsd = req.getBudgetUsd() != null ? req.getBudgetUsd() : parsedBudgetUsd(parsed);
        Integer budgetCents = budgetUsd != null ? budgetUsd * 100 : null;

        String moodText = combineMoodText(req.getMoodTags(), parsedMoodText(parsed));
        List<String> excludeTags = combineExcludeTags(req.getExcludeTags(), parsedExcludeTags(parsed));

        return RecommendationCriteria.builder()
                .players(players)
                .sessionLength(sessionLength)
                .budgetCents(budgetCents)
                .moodText(moodText)
                .excludeTags(excludeTags)
                .review(req.getReview())
                .korean(req.getKorean())
                .free(req.getFree())
                .excludeAppids(req.getExcludeAppids())
                .nlParseFailed(nlParseFailed)
                .build();
    }

    // moodTags 가 null 이면 빈 문자열로 취급하고 파싱된 moodText 와 공백으로 이어붙인 뒤 trim 함
    private String combineMoodText(List<String> moodTags, String parsedMoodText) {
        String formMood = (moodTags == null || moodTags.isEmpty()) ? "" : String.join(" ", moodTags);
        String nlMood = parsedMoodText == null ? "" : parsedMoodText;
        return (formMood + " " + nlMood).trim();
    }

    // 폼과 파싱 결과의 제외 태그를 순서를 보존한 합집합으로 만듦
    private List<String> combineExcludeTags(List<String> formTags, List<String> parsedTags) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (formTags != null) {
            merged.addAll(formTags);
        }
        if (parsedTags != null) {
            merged.addAll(parsedTags);
        }
        return new ArrayList<>(merged);
    }

    private Integer parsedPlayers(ParsedConstraints parsed) {
        return parsed == null ? null : parsed.players();
    }

    private SessionLength parsedSessionLength(ParsedConstraints parsed) {
        return parsed == null ? null : parsed.sessionLength();
    }

    private Integer parsedBudgetUsd(ParsedConstraints parsed) {
        return parsed == null ? null : parsed.budgetUsd();
    }

    private String parsedMoodText(ParsedConstraints parsed) {
        return parsed == null ? null : parsed.moodText();
    }

    private List<String> parsedExcludeTags(ParsedConstraints parsed) {
        return parsed == null ? null : parsed.excludeTags();
    }
}
