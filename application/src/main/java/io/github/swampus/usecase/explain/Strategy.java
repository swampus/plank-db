package io.github.swampus.usecase.explain;

import io.github.swampus.exception.QuantumInvalidRequestException;

/**
 * Strategy for choosing the number of Grover iterations.
 *
 * AUTO  – compute floor(π/4 * sqrt(N/M)) where N is collection size, M is #marked states.
 * FIXED – use the provided 'iterations' (min 1). If not provided, falls back to 1.
 */
public enum Strategy {

    AUTO {
        @Override
        public int selectIterations(int N, int M, Integer fixed) {
            if (M <= 0) return 1;
            double val = Math.PI / 4.0 * Math.sqrt((double) N / (double) M);
            int k = (int) Math.floor(val);
            return Math.max(1, k);
        }
    },

    FIXED {
        @Override
        public int selectIterations(int N, int M, Integer fixed) {
            return Math.max(1, fixed != null ? fixed : 1);
        }
    };

    /** Choose iterations according to the strategy. */
    public abstract int selectIterations(int N, int M, Integer fixed);

    /** Default to AUTO when null. */
    public static Strategy defaultIfNull(Strategy s) {
        return s == null ? AUTO : s;
    }

    /** Optional helper if need parse from string. */
    public static Strategy fromString(String s) {
        if (s == null) return AUTO;
        String v = s.trim().toUpperCase();
        return switch (v) {
            case "AUTO" -> AUTO;
            case "FIXED" -> FIXED;
            default -> throw new QuantumInvalidRequestException("Unknown strategy: " + s);
        };
    }
}

