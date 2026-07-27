package SteamGame.recommend.infra.embedding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 유사도 상위 K 후보 중 need 개를 균일 랜덤 샘플한다(다양성 우선).
 * 후보 전체가 이미 최근접이라 어느 걸 뽑아도 관련성은 유지되고, '다시 추천'이 매번 신선해진다.
 */
public final class VectorSampler {

    private VectorSampler() {}

    public static <T> List<T> sample(List<T> pool, int need, Random rnd) {
        if (pool == null || pool.isEmpty()) {
            return new ArrayList<>();
        }
        if (pool.size() <= need) {
            return new ArrayList<>(pool);
        }
        List<T> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, rnd);
        return new ArrayList<>(copy.subList(0, need));
    }
}
