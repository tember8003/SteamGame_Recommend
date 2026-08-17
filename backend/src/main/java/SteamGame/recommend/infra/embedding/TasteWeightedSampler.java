package SteamGame.recommend.infra.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

// 취향 벡터와의 코사인을 softmax 가중치로 바꿔 비복원 가중 추출한다
// 벡터 없는 후보는 코사인 0 중립이라 배제되지 않는다, 취향이 없으면 호출부가 균일 샘플러로 우회한다
// tau 는 양수여야 하고 0 에 가까우면 남은 최대 코사인 후보가 사실상 확정 선택된다
public final class TasteWeightedSampler {

    private TasteWeightedSampler() {}

    // 둘 중 하나가 null 이거나 노름이 0 이면 0 을 반환함
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null) {
            return 0.0;
        }
        int len = Math.min(a.length, b.length);
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < len; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0.0 || nb == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    // items 를 vectorOf 로 벡터화해 taste 와의 코사인 가중으로 need 개를 비복원 추출함
    public static <T> List<T> sample(List<T> items, Function<T, float[]> vectorOf,
                                     float[] taste, int need, double tau, Random rnd) {
        if (items == null || items.isEmpty() || need <= 0) {
            return new ArrayList<>();
        }
        if (items.size() <= need) {
            return new ArrayList<>(items);
        }

        // 후보별 코사인을 미리 계산함, 벡터 없으면 0 중립
        List<T> remaining = new ArrayList<>(items);
        List<Double> cos = new ArrayList<>(remaining.size());
        for (T item : remaining) {
            cos.add(cosine(vectorOf.apply(item), taste));
        }

        // tau 가 0 이하로 잘못 설정되면 0 나눗셈으로 NaN 이 되므로 아주 작은 양수로 클램프함, 그 경우 그리디에 수렴함
        double safeTau = tau > 0 ? tau : 1e-9;

        List<T> picked = new ArrayList<>(need);
        for (int draw = 0; draw < need; draw++) {
            // 남은 후보의 최대 코사인을 빼서 지수를 안정화함, 오버플로를 막고 비율은 불변
            double maxCos = Double.NEGATIVE_INFINITY;
            for (double c : cos) {
                if (c > maxCos) {
                    maxCos = c;
                }
            }
            double total = 0.0;
            double[] weights = new double[remaining.size()];
            for (int i = 0; i < remaining.size(); i++) {
                double w = Math.exp((cos.get(i) - maxCos) / safeTau);
                weights[i] = w;
                total += w;
            }

            // 누적 가중치로 룰렛 선택함
            double r = rnd.nextDouble() * total;
            int chosen = remaining.size() - 1;
            double acc = 0.0;
            for (int i = 0; i < remaining.size(); i++) {
                acc += weights[i];
                if (r < acc) {
                    chosen = i;
                    break;
                }
            }

            picked.add(remaining.remove(chosen));
            cos.remove(chosen);
        }
        return picked;
    }
}
