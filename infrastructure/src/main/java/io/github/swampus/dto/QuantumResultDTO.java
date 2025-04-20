package io.github.swampus.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class QuantumResultDTO {

    @JsonProperty("quantum_result")
    private QuantumResult quantumResult;

    @JsonProperty("scientific_notes")
    private ScientificNotes scientificNotes;

    @Data
    public static class QuantumResult {
        @JsonProperty("matched_key")
        private String matchedKey;

        @JsonProperty("matched_value")
        private String matchedValue;

        @JsonProperty("matched_index")
        private Integer matchedIndex;

        @JsonProperty("top_measurement")
        private String topMeasurement;

        @JsonProperty("oracle_expression")
        private String oracleExpression;

        @JsonProperty("num_qubits")
        private int numQubits;

        private Map<String, Double> probabilities;

        @JsonProperty("confidence_score")
        private double confidenceScore;

        @JsonProperty("execution_time_ms")
        private int executionTimeMs;

        @JsonProperty("oracle_depth")
        private int oracleDepth;

        private int iterations;
        private String note;
    }

    @Data
    public static class ScientificNotes {
        private String principle;
        private String theory;

        @JsonProperty("circuit_behavior")
        private String circuitBehavior;

        @JsonProperty("confidence_interpretation")
        private String confidenceInterpretation;

        @JsonProperty("qubit_commentary")
        private String qubitCommentary;

        @JsonProperty("encoding_map")
        private Map<String, String> encodingMap;

        @JsonProperty("used_iterations")
        private int usedIterations;
    }
}
