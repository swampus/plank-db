package io.github.swampus.quantum;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.port.out.QuantumDryRunnerPort;

import java.io.IOException;
import java.util.*;

/**
 * Invokes /app/python/explain.py to compute a dry-run distribution for the Explain plan.
 * <p>
 * CLI:
 * python /app/python/explain.py
 * --qubits <plan.numQubits>
 * --states <comma-separated plan.markedStates>   (e.g. "10" or "01,10")
 * --iterations <plan.iterationsUsed>
 * --shots <plan.backend.shots>
 * [--seed <plan.backend.seed>]
 * <p>
 * Expected JSON (stdout):
 * {
 * "top_measurement": "10",
 * "probabilities": { "10": 0.86, "11": 0.14 },
 * "confidence": 0.86,
 * "execution_time_ms": 17
 * }
 */

public final class PythonQuantumDryRunner implements QuantumDryRunnerPort {
    private final ObjectMapper om;
    private final io.github.swampus.quantum.QuantumProcessRunner runner;

    public PythonQuantumDryRunner(ObjectMapper om,
                                  io.github.swampus.quantum.QuantumProcessRunner runner) {
        this.om = Objects.requireNonNull(om);
        this.runner = Objects.requireNonNull(runner);
    }

    @Override
    public DryRunResult dryRun(QuantumPlan plan, DryRunOptions options) {
        // формируем аргументы explain.py
        var args = new ArrayList<String>(16);
        args.add("--qubits");
        args.add(String.valueOf(plan.numQubits()));
        args.add("--states");
        args.add(String.join(",", plan.markedStates()));
        args.add("--iterations");
        args.add(String.valueOf(plan.iterationsUsed()));
        args.add("--shots");
        args.add(String.valueOf(plan.backend().shots()));
        if (plan.backend().seed() != null) {
            args.add("--seed");
            args.add(String.valueOf(plan.backend().seed()));
        }

        // запускаем через твой раннер (он сам возьмёт QUANTUM_PYTHON_EXEC из ENV)
        String stdout = runner.run("/app/python/explain.py", args);

        // парсим JSON -> DryRunResult
        try {
            var n = om.readTree(stdout);
            String top = n.path("top_measurement").isMissingNode() ? null : n.get("top_measurement").asText(null);

            var probs = new LinkedHashMap<String, Double>();
            var p = n.get("probabilities");
            if (p != null && p.isObject()) {
                p.fields().forEachRemaining(e -> probs.put(e.getKey(), e.getValue().asDouble()));
            }

            Double conf = n.has("confidence") ? n.get("confidence").asDouble(0.0) : 0.0;
            Long ms = n.has("execution_time_ms") ? n.get("execution_time_ms").asLong() : null;

            return new DryRunResult(
                    top,
                    probs,
                    conf,
                    ms,
                    null, null, null, null,
                    true, 0, null, null,
                    stdout
            );
        } catch (Exception parse) {
            throw new io.github.swampus.exception.QuantumExternalServiceException("Explain dry-run parse error", parse);
        }
    }


        // ---- helpers -------------------------------------------------------------

    private static Map<String, Double> readProbabilities(JsonNode node) {
        if (node == null || !node.isObject()) return Collections.emptyMap();
        // Preserve iteration order (LinkedHashMap) for deterministic JSON round-trip
        var map = new LinkedHashMap<String, Double>();
        node.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asDouble()));
        return map;
    }

    private static String nodeTextOrNull(JsonNode n) {
        return (n == null || n.isNull()) ? null : n.asText();
    }

    private static String nonBlank(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

// ---- small adapter interfaces (wire to your existing infra) --------------

    public interface QuantumProcessRunner {
        Result run(List<String> args) throws IOException, InterruptedException;
        record Result(int exitCode, String stdout, String stderr) {
        }
    }

    public interface QuantumEnvConfig {
        String getPythonExec();
    }
}
