package SteamGame.recommend.domain.taste.service;

import SteamGame.recommend.domain.taste.repository.TasteCandidate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// 점수 순 후보를 10개씩 구간으로 나눠 구간 안에서만 섞고,
// 앞에서부터 대표 태그(matchedTags 첫 원소) 제한을 지키며 고른 뒤 모자라면 제한 없이 채운다
public final class DiversityPicker {

    static final int BAND_SIZE = 10;

    private DiversityPicker() {
    }

    public static List<TasteCandidate> pick(List<TasteCandidate> ranked, int size, int maxPerLeadTag, Random random) {
        List<TasteCandidate> shuffled = new ArrayList<>(ranked);
        for (int from = 0; from < shuffled.size(); from += BAND_SIZE) {
            Collections.shuffle(shuffled.subList(from, Math.min(from + BAND_SIZE, shuffled.size())), random);
        }

        List<TasteCandidate> picked = new ArrayList<>();
        List<TasteCandidate> skipped = new ArrayList<>();
        Map<String, Integer> perLead = new HashMap<>();
        for (TasteCandidate candidate : shuffled) {
            if (picked.size() >= size) {
                break;
            }
            String lead = candidate.matchedTags().isEmpty() ? "" : candidate.matchedTags().get(0);
            if (perLead.getOrDefault(lead, 0) < maxPerLeadTag) {
                picked.add(candidate);
                perLead.merge(lead, 1, Integer::sum);
            } else {
                skipped.add(candidate);
            }
        }
        for (TasteCandidate candidate : skipped) {
            if (picked.size() >= size) {
                break;
            }
            picked.add(candidate);
        }
        return picked;
    }
}
