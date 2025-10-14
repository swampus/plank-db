package io.github.swampus.dto.explain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;
import java.util.Map;

@Schema(name = "ExplainPlanResponse")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExplainPlanResponse {

    @Schema(description = "Deterministic Grover plan")
    private PlanDTO plan;

    @Schema(description = "Optional dry-run result (local backend only)")
    private DryRunDTO dryRun;

    // -------- nested DTOs --------

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PlanDTO {
        @Schema(example = "KEY")
        private String mode;

        @Schema(description = "Key or range label", example = "k2")
        private String targetLabel;

        private Map<String, String> encodingMap;
        private List<String> markedStates;

        @Schema(example = "1")
        private Integer estimatedM;

        @Schema(example = "4")
        private Integer collectionSizeN;

        @Schema(example = "2")
        private Integer numQubits;

        @Schema(example = "1")
        private Integer optimalIterations;

        @Schema(example = "1")
        private Integer iterationsUsed;

        @Schema(example = "(x01)")
        private String oracleExpression;

        @Schema(example = "4")
        private Integer estimatedOracleDepth;

        private Map<String, Integer> estimatedGateCounts;

        private BackendDTO backend;

        private List<String> notes;

        @Schema(description = "Deterministic plan identifier (optional)", example = "a1b2c3d4e5f6")
        private String planId;

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class BackendDTO {
            private String name;
            private Integer shots;
            private Integer seed;
            private Boolean noiseModel;
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DryRunDTO {
        private String topMeasurement;
        private Map<String, Double> probabilities;
        private Map<String, Double> probabilitiesNoisy; // nullable if noise==0
        private Double confidenceScore;
        private Long executionTimeMs;

        private List<TopHitDTO> topK; // optional ranked states
        private String circuitPngB64;     // optional base64
        private String histogramPngB64;   // optional base64
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class TopHitDTO {
        private String state;
        private Double p;
    }
}

