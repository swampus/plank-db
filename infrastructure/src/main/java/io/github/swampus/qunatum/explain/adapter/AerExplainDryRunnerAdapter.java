package io.github.swampus.qunatum.explain.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.ExecutionMode;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.port.out.QuantumDryRunnerPort;
import io.github.swampus.ports.QuantumScriptExecutor;
import io.github.swampus.quntum.DryRunResult;
import io.github.swampus.quntum.QuantumPlan;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Infra adapter that runs the local Aer "explain" Python script and parses JSON.
 * Delegates process execution to the existing QuantumScriptExecutor (QuantumProcessRunner).
 *
 * Behavior:
 * - If executionMode != LOCAL, returns null (Explain dry-run is local-only).
 * - If plan.backend().name() doesn't start with "local/", returns null.
 * - Otherwise, runs python/explain.py and parses JSON to DryRunResult.
 */
public class AerExplainDryRunnerAdapter implements QuantumDryRunnerPort {

    private final QuantumScriptExecutor processRunner; // your QuantumProcessRunner
    private final QuantumConfig config;                // provides script paths + mode
    private final ObjectMapper mapper;                 // inject a shared mapper

    public AerExplainDryRunnerAdapter(QuantumScriptExecutor processRunner,
                                      QuantumConfig config,
                                      ObjectMapper mapper) {
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
        this.config = Objects.requireNonNull(config, "config");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public DryRunResult dryRun(QuantumPlan plan) {
        // 1) Guard: only run locally; IBM returns plan-only (null dryRun)
        if (config.getQuantumExecutionMode() != ExecutionMode.LOCAL) return null;
        if (plan == null || plan.backend() == null) return null;
        String backendName = plan.backend().name();
        if (backendName == null || !backendName.startsWith("local/")) return null;

        // 2) Resolve script path (prefer dedicated explain path; fallback to localScriptPath; then default)
        String script = resolveExplainScriptPath();

        // 3) Build CLI args for explain.py
        List<String> args = new ArrayList<>(Arrays.asList(
                "--qubits", String.valueOf(plan.numQubits()),
                "--states", String.join(",", plan.markedStates()),
                "--iterations", String.valueOf(plan.optimalIterations()),
                "--shots", String.valueOf(
                        plan.backend().shots() == null ? 2048 : plan.backend().shots()
                )
        ));
        if (plan.backend().seed() != null) {
            args.add("--seed");
            args.add(String.valueOf(plan.backend().seed()));
        }

        // 4) Run via the shared process runner (handles env + exit codes)
        String stdout = processRunner.run(script, args);

        // 5) Parse JSON into DryRunResult
        return parseDryRun(stdout);
    }

    private String resolveExplainScriptPath() {
        // Optional dedicated key (add if you created it in QuantumConfig):
        // String p = config.getLocalExplainScriptPath();
        // If you haven't added a dedicated field, use localScriptPath as a fallback:
        String p = config.getLocalScriptPath();
        if (p == null || p.isBlank()) {
            // final fallback matches our Docker image layout
            return "/app/python/explain.py";
        }
        return p;
    }

    @SuppressWarnings("unchecked")
    private DryRunResult parseDryRun(String stdoutJson) {
        try {
            Map<String, Object> json = mapper.readValue(stdoutJson, new TypeReference<>() {});
            Map<String, Double> probs = Optional.ofNullable((Map<String, Object>) json.get("probabilities"))
                    .orElseGet(Collections::emptyMap)
                    .entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> ((Number) e.getValue()).doubleValue()
                    ));

            String top = (String) json.get("top_measurement");
            Double conf = (json.get("confidence") instanceof Number n) ? n.doubleValue() : null;
            Long execMs = (json.get("execution_time_ms") instanceof Number n) ? n.longValue() : null;

            return new DryRunResult(top, probs, conf, execMs);
        } catch (Exception e) {
            // Prefer returning null to avoid breaking the Explain flow on parse errors.
            return null;
        }
    }
}

