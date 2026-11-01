package SteamGame.recommend.domain.taste.service;

import SteamGame.recommend.domain.taste.repository.TasteCandidate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class DiversityPickerTest {

    private static TasteCandidate c(long appid, String lead) {
        return new TasteCandidate(appid, 100 - appid, List.of(lead, "Other"));
    }

    private static List<Long> ids(List<TasteCandidate> list) {
        return list.stream().map(TasteCandidate::appid).toList();
    }

    @Test
    void capsSameLeadTag() {
        List<TasteCandidate> ranked = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            ranked.add(c(i, "Strategy"));
        }
        ranked.add(c(7, "Colony Sim"));
        ranked.add(c(8, "Tactical"));

        List<TasteCandidate> picked = DiversityPicker.pick(ranked, 5, 2, new Random(42));

        assertThat(picked).hasSize(5);
        assertThat(picked.stream().filter(p -> p.matchedTags().get(0).equals("Strategy")).count()).isEqualTo(3);
        assertThat(picked.stream().filter(p -> p.matchedTags().get(0).equals("Colony Sim")).count()).isEqualTo(1);
        assertThat(picked.stream().filter(p -> p.matchedTags().get(0).equals("Tactical")).count()).isEqualTo(1);
    }

    @Test
    void noDuplicates() {
        List<TasteCandidate> ranked = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            ranked.add(c(i, "Same"));
        }

        List<TasteCandidate> picked = DiversityPicker.pick(ranked, 5, 2, new Random(42));

        assertThat(ids(picked)).doesNotHaveDuplicates().hasSize(5);
    }

    @Test
    void fewerCandidatesThanSize() {
        List<TasteCandidate> ranked = List.of(c(1, "A"), c(2, "B"), c(3, "C"));

        assertThat(DiversityPicker.pick(ranked, 5, 2, new Random(42))).hasSize(3);
    }

    @Test
    void emptyInEmptyOut() {
        assertThat(DiversityPicker.pick(List.of(), 5, 2, new Random(42))).isEmpty();
    }

    @Test
    void shufflesWithinBands() {
        List<TasteCandidate> ranked = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            ranked.add(c(i, "Tag" + i));
        }

        List<Long> seed1 = ids(DiversityPicker.pick(ranked, 5, 2, new Random(1)));
        List<Long> seed1Again = ids(DiversityPicker.pick(ranked, 5, 2, new Random(1)));
        List<Long> seed2 = ids(DiversityPicker.pick(ranked, 5, 2, new Random(2)));

        assertThat(seed1).isEqualTo(seed1Again);
        assertThat(seed1).isNotEqualTo(seed2);
    }

    @Test
    void topBandStaysOnTop() {
        List<TasteCandidate> ranked = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            ranked.add(c(i, "Tag" + i));
        }

        for (long seed = 0; seed < 20; seed++) {
            assertThat(ids(DiversityPicker.pick(ranked, 5, 2, new Random(seed)))).allMatch(id -> id <= 10);
        }
    }
}
