package io.github.swampus.quantum.explain.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.port.out.QuantumDryRunnerPort;
import io.github.swampus.quantum.DryRunResult;
import io.github.swampus.quantum.QuantumPlan;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AerExplainDryRunnerAdapter implements QuantumDryRunnerPort {

    // Jackson in infrastructure (OK for Clean Architecture)
    private final ObjectMapper mapper = new ObjectMapper();

    // Python executable path; supports env fallback
    @Value("${quantum.python-executable:${QUANTUM_PYTHON_EXEC:python3}}")
    private String pythonExec;

    // Use a dedicated property for explain script (do not reuse runtime search script)
    @Value("${quantum.local-explain-script-path:${QUANTUM_LOCAL_EXPLAIN_SCRIPT_PATH:/app/python/explain.py}}")
    private String scriptPath;

    // Hard timeout to avoid zombie python processes
    @Value("${quantum.dryrun.timeout-seconds:120}")
    private int timeoutSeconds;

    @PostConstruct
    void checkAerImport() {
        // Verify that qiskit and qiskit_aer can be imported at runtime
        try {
            Process p = new ProcessBuilder(pythonExec, "-c",
                    "import sys; " +
                            "print('PY', sys.version.replace('\\n',' ')); " +
                            "import qiskit; print('QISKIT', getattr(qiskit, '__version__', 'n/a')); " +
                            "import qiskit_aer as aer; print('AER', getattr(aer, '__version__', 'n/a'))"
            ).start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String err = new String(p.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

            if (p.exitValue() == 0) {
                log.info("[DryRun:init] {}", out.trim());
            } else {
                log.error("[DryRun:init] Aer import failed. exitCode={} stderr=\n{}", p.exitValue(), err);
            }
        } catch (Exception e) {
            log.warn("[DryRun:init] Aer import check threw: {}", e.toString());
        }
    }


    @Override
    public DryRunResult dryRun(QuantumPlan plan, DryRunOptions options) {

        List<String> cmd = buildCmd(plan, options);

        final String callId = UUID.randomUUID().toString().substring(0, 8);

        Process p = null;
        Integer exit = null;
        String stdout = "";
        String stderr = "";

        // Use a tiny executor just to read stdout/stderr concurrently (prevents deadlocks)
        var exec = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "dryrun-gobbler");
            t.setDaemon(true);
            return t;
        });

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            // Keep streams separated to return meaningful diagnostics
            p = pb.start();

            Future<String> outFuture = exec.submit(gobble(p.getInputStream()));
            Future<String> errFuture = exec.submit(gobble(p.getErrorStream()));

            boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                // Force kill and collect whatever was produced by the process
                p.destroyForcibly();
                stdout = safeGet(outFuture);
                stderr = safeGet(errFuture);
                log.error("Dry-run timeout after {}s. CMD={}", timeoutSeconds, String.join(" ", cmd));
                return errorResult("Python dry-run timeout", 124, stdout, stderr);
            }

            // Process finished, collect outputs
            exit = p.exitValue();
            stdout = safeGet(outFuture);
            stderr = safeGet(errFuture);

            // Try to parse JSON even when exit != 0 (python might emit { "error": "..." })
            Map<String, Object> json = tryParseJson(stdout);

            if (exit != 0) {
                String errMsg = (json != null && json.get("error") != null)
                        ? String.valueOf(json.get("error"))
                        : extractPythonErrorSummary(stderr, exit);

                log.error("Dry-run failed. exitCode={}, error='{}'", exit, errMsg);
                log.error("[DryRun:{}] Python failed: exitCode={} summary='{}'", callId, exit, errMsg);
                log.error("[DryRun:{}] --- STDERR BEGIN ---\n{}\n--- STDERR END ---",
                        callId, truncate(stderr, 20000));
                return errorResult(errMsg, exit, stdout, stderr);
            }



            // Exit code is 0: stdout must be valid JSON
            if (json == null) {
                log.error("Dry-run JSON parse failed while exitCode==0. stdout='{}'", truncate(stdout, 4000));
                return errorResult("JSON parse error (empty or invalid stdout)", 0, stdout, stderr);
            }

            Map<String, Double> probs      = castProbMap(json.get("probabilities"));
            String top                     = asString(json.get("top_measurement"));
            Double conf                    = asDouble(json.get("confidence"));
            Long execMs                    = asLong(json.get("execution_time_ms"));
            Map<String, Double> probsNoisy = castProbMap(json.get("probabilities_noisy"));
            List<DryRunResult.TopHit> topK = parseTopK(json.get("topK"));
            String circuitB64              = asString(json.get("circuit_png_b64"));
            String histB64                 = asString(json.get("histogram_png_b64"));

            return new DryRunResult(
                    top, probs, conf, execMs, probsNoisy, topK, circuitB64, histB64,
                    true, 0, null, null, null
            );
        } catch (Exception e) {
            log.error("Dry-run threw exception", e);
            return errorResult("Adapter exception: " + e.getMessage(),
                    exit == null ? -1 : exit,
                    nullSafe(stdout), nullSafe(stderr));
        } finally {
            if (p != null) {
                try { p.destroyForcibly(); } catch (Exception ignore) {}
            }
            exec.shutdownNow();
        }
    }

    @Override
    public DryRunResult dryRun(QuantumPlan plan) {
        return dryRun(plan, DryRunOptions.defaults());
    }

    // ----------------- helpers -----------------

    private List<String> buildCmd(QuantumPlan plan, DryRunOptions options) {
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
            if (options.render()) {
                cmd.add("--render");
            }
            if (options.noise() > 0.0) {
                cmd.add("--noise"); cmd.add(String.valueOf(options.noise()));
            }
            if (options.topK() != null) {
                cmd.add("--topk"); cmd.add(String.valueOf(options.topK()));
            }
        }
        return cmd;
    }

    private Callable<String> gobble(InputStream is) {
        return () -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return br.lines().collect(Collectors.joining("\n"));
            }
        };
    }

    private String safeGet(Future<String> f) {
        try {
            return f.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to collect stream: {}", e.toString());
            return "";
        }
    }

    private Map<String, Object> tryParseJson(String stdout) {
        try {
            if (stdout == null || stdout.isBlank()) return null;
            return mapper.readValue(stdout, new TypeReference<>() {});
        } catch (Exception ignore) {
            return null;
        }
    }

    private DryRunResult errorResult(String message, Integer exitCode, String stdout, String stderr) {
        return new DryRunResult(
                null, null, null, null, null, null, null, null,
                false,
                exitCode == null ? -1 : exitCode,
                message,
                nullSafe(stderr),
                nullSafe(stdout)
        );
    }

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
                try {
                    res.put(e.getKey(), Double.parseDouble(String.valueOf(v)));
                } catch (NumberFormatException nfe) {
                    log.warn("Skipping non-numeric probability value: key={}, value={}", e.getKey(), v);
                }
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

    private String asString(Object o) {
        return (o == null) ? null : String.valueOf(o);
    }

    private Double asDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            log.warn("Non-numeric double: {}", o);
            return null;
        }
    }

    private Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            log.warn("Non-numeric long: {}", o);
            return null;
        }
    }

    private static String firstLineOr(String s, String fallback) {
        if (s == null || s.isBlank()) return fallback;
        int i = s.indexOf('\n');
        return (i < 0) ? s : s.substring(0, i);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + " ...";
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    // --- helper: summarize Python traceback into a single line ---
    private static String extractPythonErrorSummary(String stderr, Integer exit) {
        // Fallback if stderr is empty
        if (stderr == null || stderr.isBlank()) {
            return "Python exited with code " + (exit == null ? "?" : exit);
        }

        // Split to non-empty trimmed lines
        java.util.List<String> lines = java.util.Arrays.stream(stderr.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        if (lines.isEmpty()) {
            return "Python exited with code " + (exit == null ? "?" : exit);
        }

        // Typical last line: <SomeError>: <message>
        java.util.regex.Pattern errPattern =
                java.util.regex.Pattern.compile("^[\\w\\._]+(?:Error|Exception):\\s+.+$");

        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (errPattern.matcher(line).matches()) {
                return line;
            }
        }
        // If nothing matched, return the last non-empty line
        return lines.get(lines.size() - 1);
    }
}
