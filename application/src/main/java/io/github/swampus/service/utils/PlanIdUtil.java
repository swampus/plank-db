package io.github.swampus.service.utils;

import io.github.swampus.quantum.BackendInfo;
import io.github.swampus.quantum.QueryMode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

public final class PlanIdUtil {
    private PlanIdUtil() {}

    public static String computeId(
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
            BackendInfo backend
    ) {
        String canonical = canonicalize(
                mode, targetLabel, encodingMap, markedStates, estimatedM, collectionSizeN,
                numQubits, optimalIterations, iterationsUsed, oracleExpression,
                estimatedOracleDepth, estimatedGateCounts, backend
        );
        byte[] digest = sha256(canonical);
        return toHex(digest).substring(0, 12);
    }

    private static String canonicalize(
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
            BackendInfo backend
    ) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("v1|")
                .append(mode).append('|')
                .append(nullToEmpty(targetLabel)).append('|');


        encodingMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(e.getKey()).append('=').append(e.getValue()).append(';'));
        sb.append('|');


        markedStates.stream().sorted().forEach(s -> sb.append(s).append(','));
        sb.append('|');

        sb.append(estimatedM).append('|')
                .append(collectionSizeN).append('|')
                .append(numQubits).append('|')
                .append(optimalIterations).append('|')
                .append(iterationsUsed).append('|')
                .append(nullToEmpty(oracleExpression)).append('|')
                .append(estimatedOracleDepth).append('|');

        estimatedGateCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(e.getKey()).append('=').append(e.getValue()).append(';'));
        sb.append('|');

        // backend
        if (backend != null) {
            sb.append(nullToEmpty(backend.name())).append('|')
                    .append(backend.shots()).append('|')
                    .append(backend.seed()).append('|')
                    .append(backend.noiseModel());
        }
        return sb.toString();
    }

    private static byte[] sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

