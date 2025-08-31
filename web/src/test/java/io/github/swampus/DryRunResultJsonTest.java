package io.github.swampus;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.JacksonDryRunConfig;
import io.github.swampus.quantum.DryRunResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
@Import(JacksonDryRunConfig.class)
class DryRunResultJsonTest {
    @Autowired ObjectMapper mapper;

    @Test
    void shouldSerializeWithConfidenceScoreAndNoConfidenceField() throws Exception {
        DryRunResult r = new DryRunResult(
                "01",
                Map.of("01", 0.9),
                0.9,
                12L,
                null, null, null, null,
                true, 0, null, null, null
        );

        String json = mapper.writeValueAsString(r);
        assertThat(json).contains("\"confidence_score\":0.9");
        assertThat(json).doesNotContain("\"confidence\":");
        assertThat(json).contains("\"top_measurement\":\"01\"");
    }
}

