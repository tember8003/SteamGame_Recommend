package SteamGame.recommend.infra.embedding;

import java.util.List;

/**
 * float[] 임베딩과 pgvector 리터럴("[a,b,c]") 사이 변환 유틸.
 * 포맷에 Float.toString 을 써서 콤마 소수점 로케일에도 '.' 소수점을 보장한다.
 */
public final class VectorLiterals {

    private VectorLiterals() {}

    // float[] -> "[0.12,0.34,...]"
    public static String toPgVector(float[] vec) {
        StringBuilder sb = new StringBuilder(vec.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            // Float.toString 은 항상 '.' 소수점 (로케일 무관)
            sb.append(Float.toString(vec[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    // "[0.12,0.34,...]" -> float[]
    public static float[] parse(String literal) {
        if (literal == null) {
            return null;
        }
        String body = literal.trim();
        if (body.startsWith("[")) {
            body = body.substring(1);
        }
        if (body.endsWith("]")) {
            body = body.substring(0, body.length() - 1);
        }
        body = body.trim();
        if (body.isEmpty()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vec[i] = Float.parseFloat(parts[i].trim());
        }
        return vec;
    }

    // 성분별 평균 (프로필/최근플레이 taste 벡터 폴백용)
    public static float[] mean(List<float[]> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return null;
        }
        int dim = vectors.get(0).length;
        double[] sum = new double[dim];
        int counted = 0;
        for (float[] v : vectors) {
            if (v == null || v.length != dim) {
                continue;
            }
            for (int i = 0; i < dim; i++) {
                sum[i] += v[i];
            }
            counted++;
        }
        if (counted == 0) {
            return null;
        }
        float[] mean = new float[dim];
        for (int i = 0; i < dim; i++) {
            mean[i] = (float) (sum[i] / counted);
        }
        return mean;
    }
}
