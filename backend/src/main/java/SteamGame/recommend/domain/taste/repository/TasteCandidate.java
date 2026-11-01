package SteamGame.recommend.domain.taste.repository;

import java.util.List;

// 후보 한 건, matchedTags는 기여(가중치 큰 순)대로 정렬됨
public record TasteCandidate(long appid, double score, List<String> matchedTags) {
}
