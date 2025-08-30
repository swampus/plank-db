package io.github.swampus.quantum;

import java.util.Map;

public record DryRunResult(String topMeasurement,
                           Map<String, Double> probabilities,
                           Double confidenceScore,
                           Long executionTimeMs) {

}
