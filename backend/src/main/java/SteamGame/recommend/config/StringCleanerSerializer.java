package SteamGame.recommend.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// JacksonConfig 에 더는 등록하지 않는다(정상 UTF-8 을 ® 로 파괴하던 유물)
// 회귀 테스트가 오염 인코더 픽스처로 쓰려고 클래스만 남긴다, 스프링 빈으로 등록하지 않음
public class StringCleanerSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
        if(value == null) {
            generator.writeNull();
            return;
        }

        String cleanedValue = fixEncodingIssues(value);
        generator.writeString(cleanedValue);
    }

    private String fixEncodingIssues(String text) {
        if(text == null) return null;

        try {
            // Attempt to convert incorrect encoding to UTF-8
            byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
            String utf8String = new String(bytes, StandardCharsets.UTF_8);

            // Direct mapping if there are still broken characters
            return cleanKnownIssues(utf8String);
        } catch (Exception e) {
            // Direct mapping in case of conversion failure
            return cleanKnownIssues(text);
        }
    }

    /**
     * check if it is a URL
     */
    private boolean isUrl(String text) {
        if (text == null) return false;
        return text.startsWith("http://") || text.startsWith("https://");
    }

    /**
     * Method to determine if it is an image_url field (if the current field name is known)
     */
    private boolean isImageUrlField(SerializerProvider serializers) {
        try {
            String currentFieldName = serializers.getGenerator().getOutputContext().getCurrentName();
            return "imageUrl".equals(currentFieldName) ||
                    "image_url".equals(currentFieldName) ||
                    "url".equals(currentFieldName);
        } catch (Exception e) {
            return false;
        }
    }

    private String cleanKnownIssues(String text) {
        if(text == null) return null;

        if (isUrl(text)) {
            return text
                    .replace("�", "®")
                    .replace("짰","®")
                    .replace("Â®", "®")
                    .replace("â„¢", "™")
                    .replace("Â©", "©")
                    .replace("Â", "")
                    .replace("â€™", "'")
                    .replace("â€œ", "\"")
                    .replace("â€", "\"")
                    .replace("�꽓","™")
                    .trim();
        }

        return text
                .replace("??","™")
                .replace("?","®")
                .replace("�", "®")
                .replace("짰","®")
                .replace("Â®", "®")
                .replace("â„¢", "™")
                .replace("Â©", "©")
                .replace("Â", "")
                .replace("â€™", "'")
                .replace("â€œ", "\"")
                .replace("â€", "\"")
                .replace("�꽓","™")
                .trim();
    }
}