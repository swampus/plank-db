package io.github.swampus.quantum;

import java.util.List;
import java.util.Map;

public record QuantumPlan(
        QueryMode mode,
        String targetLabel,
        Map<String, String> encodingMap,
        List<String> markedStates,
        int estimatedM,
        int collectionSizeN,
        int numQubits,
        int optimalIterations,
        int iterationsUsed,
        String oracleExpression,
        int estimatedOracleDepth,
        Map<String, Integer> estimatedGateCounts,
        BackendInfo backend,
        List<String> note,
        String planId
) {}

