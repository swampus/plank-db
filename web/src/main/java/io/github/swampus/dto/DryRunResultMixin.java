package io.github.swampus.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.swampus.quantum.DryRunResult;

import java.util.List;
import java.util.Map;

/**
 * Jackson MixIn for DryRunResult to control JSON shape without touching the domain.
 */
public abstract class DryRunResultMixin {

    // Map record accessors -> snake_case JSON
    @JsonProperty("top_measurement") abstract String topMeasurement();
    @JsonProperty("probabilities") abstract Map<String, Double> probabilities();

    // Hide raw "confidence" and expose alias "confidence_score"
    @JsonIgnore abstract Double confidence();
    @JsonProperty("confidence_score") abstract Double confidenceScore();

    @JsonProperty("execution_time_ms") abstract Long executionTimeMs();
    @JsonProperty("probabilities_noisy") abstract Map<String, Double> probabilitiesNoisy();
    @JsonProperty("top_k") abstract List<DryRunResult.TopHit> topK();
    @JsonProperty("circuit_png_b64") abstract String circuitPngB64();
    @JsonProperty("histogram_png_b64") abstract String histogramPngB64();

    // Diagnostics
    @JsonProperty("success") abstract boolean success();
    @JsonProperty("exit_code") abstract Integer exitCode();
    @JsonProperty("error_message") abstract String errorMessage();
    @JsonProperty("stderr") abstract String stderr();
    @JsonProperty("raw_stdout") abstract String rawStdout();
}
