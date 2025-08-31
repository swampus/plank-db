package io.github.swampus.quantum;

import java.util.Map;


import java.util.List;

/**
 * Result of a local Aer dry-run.
 * Optional fields (noisy probs, images, topK) are present only when requested.
 */
public record DryRunResult(
        String topMeasurement,
        Map<String, Double> probabilities,
        Double confidenceScore,
        Long executionTimeMs,

        // Optional: present if noise > 0
        Map<String, Double> probabilitiesNoisy,

        // Optional: present if topK was requested
        List<TopHit> topK,

        // Optional: present if render=true
        String circuitPngB64,
        String histogramPngB64
) {
    /** Ranked candidate from the histogram (state, probability). */
    public record TopHit(String state, Double p) {}
}

