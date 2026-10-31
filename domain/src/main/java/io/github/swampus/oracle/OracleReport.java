package io.github.swampus.oracle;

import java.util.List;
import java.util.Map;

/** Measured values are nullable for explain, which compiles but never executes. */
public record OracleReport(
        String compilerVersion, int schemaVersion, Map<String, Object> normalizedPredicate,
        String oracleFingerprint, String backend, String qiskitVersion, String aerVersion,
        boolean simulated, boolean cacheHit, Integer analyticalSolutionCount,
        int iterations, int shots, int seed, Double theoreticalSuccessProbability,
        Double observedValidProbability, List<Measurement> measurements, Measurement topMeasurement,
        ClassicalBaseline classicalBaseline, Metrics metrics, Timings timings, String oracleCircuit,
        List<String> warnings) {
    public record Measurement(String bits, int value, int count, double probability, boolean valid) {}
    public record ClassicalBaseline(List<Integer> validStates, int solutionCount, double elapsedMs) {}
    public record Metrics(int searchQubits, int ancillaQubits, int totalQubits, long statevectorBytes,
                          int oracleDepth, int totalDepth, Map<String, Integer> gateCounts, int twoQubitGates) {}
    public record Timings(double normalizationMs, double compilationMs, double cacheLookupAndWriteMs,
                          double circuitConstructionMs, double transpilationMs, double simulationMs,
                          double verificationMs, double pythonTotalMs) {}
}
