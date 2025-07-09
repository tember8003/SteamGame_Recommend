package SteamGame.recommend.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
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

    private String cleanKnownIssues(String text) {
        if(text == null) return null;

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
