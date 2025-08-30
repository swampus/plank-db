package io.github.swampus.quntum;

import java.util.Map;

public record DryRunResult(String topMeasurement,
                           Map<String, Double> probabilities,
                           Double confidenceScore,
                           Long executionTimeMs) {

}
