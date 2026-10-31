package io.github.swampus.oracle;

public record OracleQuery(Integer width, OraclePredicate predicate, Integer iterations, Integer shots, Integer seed) {
    public void validate(int maxWidth, int maxTotalQubits, int maxShots, int maxIterations) {
        if (width == null || width < 2 || width > maxWidth) throw OracleFailure.invalid("width must be in [2, " + maxWidth + "]");
        if (predicate == null) throw OracleFailure.invalid("predicate is required");
        int nodes = predicate.validate(width, 1);
        if (nodes > 15 || width + nodes > maxTotalQubits) throw OracleFailure.invalid("predicate exceeds work-qubit budget");
        if (shots != null && (shots < 1 || shots > maxShots)) throw OracleFailure.invalid("shots outside supported bounds");
        if (iterations != null && (iterations < 0 || iterations > maxIterations)) throw OracleFailure.invalid("iterations outside supported bounds");
        if (seed != null && seed < 0) throw OracleFailure.invalid("seed must be nonnegative");
    }
}
