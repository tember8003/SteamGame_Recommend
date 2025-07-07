package SteamGame.recommend.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class StringCleanerSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
        if(value == null) {
            generator.writeNull();
            return;
        }

        String cleanedValue = cleanSpecialCharacters(value);
        generator.writeString(cleanedValue);
    }

    private String cleanSpecialCharacters(String text) {
        if(text == null) return null;

        return text.replace("짰","®")
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
