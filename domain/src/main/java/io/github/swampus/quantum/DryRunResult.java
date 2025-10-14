package io.github.swampus.quantum;

import java.util.List;
import java.util.Map;

/**
 * Pure domain record without Jackson dependencies.
 * Exposes confidenceScore() as the preferred accessor.
 */
public record DryRunResult(
        String topMeasurement,
        Map<String, Double> probabilities,
        Double confidence,
        Long executionTimeMs,
        Map<String, Double> probabilitiesNoisy,
        List<TopHit> topK,
        String circuitPngB64,
        String histogramPngB64,

        boolean success,
        Integer exitCode,
        String errorMessage,
        String stderr,
        String rawStdout
) {
    public record TopHit(String state, Double p) {}

    // Legacy 8-arg ctor (success=true, empty diagnostics)
    public DryRunResult(String topMeasurement,
                        Map<String, Double> probabilities,
                        Double confidence,
                        Long executionTimeMs,
                        Map<String, Double> probabilitiesNoisy,
                        List<TopHit> topK,
                        String circuitPngB64,
                        String histogramPngB64) {
        this(topMeasurement, probabilities, confidence, executionTimeMs,
                probabilitiesNoisy, topK, circuitPngB64, histogramPngB64,
                true, 0, null, null, null);
    }

    // Preferred alias used by tests and API
    public Double confidenceScore() {
        return confidence;
    }
}
