package io.github.swampus.quantum.explain.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.port.out.QuantumDryRunnerPort;
import io.github.swampus.quantum.DryRunResult;
import io.github.swampus.quantum.QuantumPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AerExplainDryRunnerAdapter implements QuantumDryRunnerPort {

    // Use Jackson in infrastructure (OK by clean architecture)
    private final ObjectMapper mapper = new ObjectMapper();

    // Allow both Spring property and env fallback
    @Value("${quantum.python-executable:${QUANTUM_PYTHON_EXEC:python3}}")
    private String pythonExec;

    // Local explain script path (the one that accepts --states/--iterations/etc)
    @Value("${quantum.local-script-path:${QUANTUM_LOCAL_SCRIPT_PATH:/app/python/explain.py}}")
    private String scriptPath;

    @Override
    public DryRunResult dryRun(QuantumPlan plan, DryRunOptions options) {
        try {
            // Build CLI args
            List<String> cmd = new ArrayList<>();
            cmd.add(pythonExec);
            cmd.add(scriptPath);
            cmd.add("--qubits");     cmd.add(String.valueOf(plan.numQubits()));
            cmd.add("--states");     cmd.add(String.join(",", plan.markedStates()));
            cmd.add("--iterations"); cmd.add(String.valueOf(plan.optimalIterations()));
            int shots = plan.backend().shots() == null ? 2048 : plan.backend().shots();
            cmd.add("--shots");      cmd.add(String.valueOf(shots));
            if (plan.backend().seed() != null) {
                cmd.add("--seed");   cmd.add(String.valueOf(plan.backend().seed()));
            }
            if (options != null) {
                if (options.render()) { cmd.add("--render"); }
                if (options.noise() > 0.0) {
                    cmd.add("--noise"); cmd.add(String.valueOf(options.noise()));
                }
                if (options.topK() != null) {
                    cmd.add("--topk"); cmd.add(String.valueOf(options.topK()));
                }
            }

            // Run process
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                out = br.lines().collect(Collectors.joining());
            }
            int code = p.waitFor();
            if (code != 0) {
                throw new RuntimeException("Python exited with code " + code + ", output: " + out);
            }

            // Parse JSON (tolerate absent optional fields)
            Map<String, Object> json = mapper.readValue(out, new TypeReference<>() {});
            Map<String, Double> probs        = castProbMap(json.get("probabilities"));
            String top                       = asString(json.get("top_measurement"));
            Double conf                      = asDouble(json.get("confidence"));
            Long execMs                      = asLong(json.get("execution_time_ms"));
            Map<String, Double> probsNoisy   = castProbMap(json.get("probabilities_noisy"));
            List<DryRunResult.TopHit> topK   = parseTopK(json.get("topK"));
            String circuitB64                = asString(json.get("circuit_png_b64"));
            String histB64                   = asString(json.get("histogram_png_b64"));

            // New DryRunResult signature (8 args). Pass nulls if not present.
            return new DryRunResult(top, probs, conf, execMs, probsNoisy, topK, circuitB64, histB64);
        } catch (Exception e) {
            throw new RuntimeException("Dry-run failed: " + e.getMessage(), e);
        }
    }

    // optional legacy override if interface also provides default short method
    @Override
    public DryRunResult dryRun(QuantumPlan plan) {
        return dryRun(plan, DryRunOptions.defaults());
    }

    // ---------- helpers ----------

    @SuppressWarnings("unchecked")
    private Map<String, Double> castProbMap(Object o) {
        if (o == null) return null;
        Map<String, Object> raw = (Map<String, Object>) o;
        Map<String, Double> res = new LinkedHashMap<>(raw.size());
        for (var e : raw.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Number n) {
                res.put(e.getKey(), n.doubleValue());
            } else if (v != null) {
                res.put(e.getKey(), Double.parseDouble(String.valueOf(v)));
            }
        }
        return res;
    }

    @SuppressWarnings("unchecked")
    private List<DryRunResult.TopHit> parseTopK(Object o) {
        if (o == null) return null;
        List<Map<String, Object>> arr = (List<Map<String, Object>>) o;
        List<DryRunResult.TopHit> list = new ArrayList<>(arr.size());
        for (Map<String, Object> m : arr) {
            String state = asString(m.get("state"));
            Double p     = asDouble(m.get("p"));
            list.add(new DryRunResult.TopHit(state, p));
        }
        return list;
    }

    private String asString(Object o) { return (o == null) ? null : String.valueOf(o); }
    private Double asDouble(Object o) { return (o == null) ? null : ((Number) o).doubleValue(); }
    private Long asLong(Object o)     { return (o == null) ? null : ((Number) o).longValue(); }
}
