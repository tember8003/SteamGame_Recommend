package SteamGame.recommend.domain.recommendation.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MatchRequestDTOValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test void rejectsNonPositivePlayersAndNegativeBudget() {
        MatchRequestDTO dto = new MatchRequestDTO();
        dto.setPlayers(0);
        dto.setBudgetUsd(-1);
        assertThat(validator.validate(dto)).isNotEmpty();
    }
    @Test void acceptsNullOptionalFields() {
        MatchRequestDTO dto = new MatchRequestDTO(); // 전부 미설정
        assertThat(validator.validate(dto)).isEmpty();
    }
}
