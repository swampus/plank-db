package io.github.swampus.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class QuantumResultModel {
    private QuantumResult quantumResult;
    private ScientificNotes scientificNotes;

    @Data
    public static class QuantumResult {
        private String matchedKey;
        private String matchedValue;
        private Integer matchedIndex;
        private String topMeasurement;
        private String oracleExpression;
        private int numQubits;
        private Map<String, Double> probabilities;
        private double confidenceScore;
        private int executionTimeMs;
        private int oracleDepth;
        private int iterations;
        private String note;
    }

    @Data
    public static class ScientificNotes {
        private String principle;
        private String theory;
        private String circuitBehavior;
        private String confidenceInterpretation;
        private String qubitCommentary;
        private Map<String, String> encodingMap;
        private int usedIterations;
        private List<String> rangeBounds;
    }

}

