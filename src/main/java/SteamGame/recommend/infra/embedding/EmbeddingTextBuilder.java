package SteamGame.recommend.infra.embedding;

import java.util.List;

/**
 * 게임 한 건을 임베딩용 문서 텍스트로 조립한다.
 * 크롤 도중이라 일부 필드가 비어 있을 수 있어 전 필드 null-safe 로 처리한다.
 */
public final class EmbeddingTextBuilder {

    private EmbeddingTextBuilder() {}

    public static String build(String name, String genres, List<String> tagNames,
                               String description, int descriptionMaxChars) {
        StringBuilder sb = new StringBuilder();

        if (isNotBlank(name)) {
            sb.append(name.trim());
        }
        if (isNotBlank(genres)) {
            appendLine(sb, "Genres: " + genres.trim());
        }
        if (tagNames != null && !tagNames.isEmpty()) {
            appendLine(sb, "Tags: " + String.join(", ", tagNames));
        }
        if (isNotBlank(description)) {
            appendLine(sb, truncate(description.trim(), descriptionMaxChars));
        }
        return sb.toString();
    }

    private static void appendLine(StringBuilder sb, String line) {
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(line);
    }

    private static String truncate(String s, int maxChars) {
        if (maxChars <= 0 || s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars);
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
