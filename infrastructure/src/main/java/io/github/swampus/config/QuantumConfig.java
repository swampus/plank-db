package io.github.swampus.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "quantum")
@Getter
@Setter
@Slf4j
public class QuantumConfig {

    // --- Core settings (with safe defaults) ---

    /** Absolute path to Python interpreter used to run quantum scripts. */
    private String pythonExecutable = "/opt/venv/bin/python";

    /** Execution mode: LOCAL (Aer) or IBM (remote). */
    private ExecutionMode quantumExecutionMode = ExecutionMode.LOCAL;

    /** IBM auth token (required only for IBM modes). */
    private String quantumIbmToken;

    // --- Script paths (flat keys to keep current YAML compatibility) ---

    /** LOCAL: key search script (Grover over single label). */
    private String localScriptPath = "/app/python/grover.py";

    /** LOCAL: range search script (Grover over a range). */
    private String localRangeScriptPath = "/app/python/grover_range.py";

    /** LOCAL: explain/dry-run script (AerExplain) */
    private String localExplainScriptPath = "/app/python/explain.py";

    /** IBM: key search script. */
    private String ibmScriptPath = "/app/python/grover_ibm.py";

    /** IBM: range search script. */
    private String ibmRangeScriptPath = "/app/python/grover_range_ibm.py";

    // --- Lifecycle ---

    /**
     * Validate configuration at startup. Fail-fast on critical issues
     * for the active backend only; warn for non-critical environment issues.
     */
    @PostConstruct
    public void validate() {
        // Basic required fields
        List<String> errors = new ArrayList<>();
        if (isNullOrBlank(pythonExecutable)) {
            errors.add("pythonExecutable is not set (env QUANTUM_PYTHON_EXEC).");
        }
        if (quantumExecutionMode == null) {
            errors.add("quantumExecutionMode is not set (env QUANTUM_EXECUTION_MODE).");
        }

        // Mode-specific requirements
        if (quantumExecutionMode == ExecutionMode.LOCAL) {
            require("localScriptPath (env QUANTUM_LOCAL_SCRIPT_PATH)", localScriptPath, errors);
            require("localRangeScriptPath (env QUANTUM_LOCAL_RANGE_SCRIPT_PATH)", localRangeScriptPath, errors);
            // Explain is optional but recommended; warn if missing
            if (isNullOrBlank(localExplainScriptPath)) {
                log.warn("[QuantumConfig] localExplainScriptPath is empty; falling back to localScriptPath for explain().");
            }
        } else if (quantumExecutionMode == ExecutionMode.IBM) {
            require("ibmScriptPath (env QUANTUM_IBM_SCRIPT_PATH)", ibmScriptPath, errors);
            require("ibmRangeScriptPath (env QUANTUM_IBM_RANGE_SCRIPT_PATH)", ibmRangeScriptPath, errors);
            require("quantumIbmToken (env QUANTUM_IBM_TOKEN)", quantumIbmToken, errors);
        } else if (quantumExecutionMode == ExecutionMode.IBM_REAL_PC) {
            errors.add("IBM_REAL_PC mode is not supported yet (requires dynamic script handling).");
        }

        if (!errors.isEmpty()) {
            // Build a human-friendly message and fail fast
            String msg = "[QuantumConfig] Misconfiguration:\n - " + String.join("\n - ", errors);
            throw new IllegalStateException(msg);
        }

        // Soft environment checks (do not fail the app)
        softCheckExists("pythonExecutable", pythonExecutable);
        if (quantumExecutionMode == ExecutionMode.LOCAL) {
            softCheckExists("localScriptPath", localScriptPath);
            softCheckExists("localRangeScriptPath", localRangeScriptPath);
            if (!isNullOrBlank(localExplainScriptPath)) {
                softCheckExists("localExplainScriptPath", localExplainScriptPath);
            }
        } else if (quantumExecutionMode == ExecutionMode.IBM) {
            softCheckExists("ibmScriptPath", ibmScriptPath);
            softCheckExists("ibmRangeScriptPath", ibmRangeScriptPath);
        }

        // Summary (mask IBM token if present)
        log.info("[QuantumConfig] mode={}, python='{}', keyScript='{}', rangeScript='{}', explainScript='{}', ibmToken={}",
                quantumExecutionMode,
                pythonExecutable,
                resolveKeyScriptPath(),
                resolveRangeScriptPath(),
                resolveExplainScriptPath(),
                mask(quantumIbmToken));
    }

    // --- Resolvers used by adapters/use cases ---

    /** Resolve script path for KEY search based on current execution mode. */
    public String resolveKeyScriptPath() {
        return (quantumExecutionMode == ExecutionMode.LOCAL) ? localScriptPath : ibmScriptPath;
    }

    /** Resolve script path for RANGE search based on current execution mode. */
    public String resolveRangeScriptPath() {
        return (quantumExecutionMode == ExecutionMode.LOCAL) ? localRangeScriptPath : ibmRangeScriptPath;
    }

    /**
     * Resolve script path for EXPLAIN (dry-run) on LOCAL backend.
     * Falls back to localScriptPath if explicit explain path is missing.
     * Returns null for non-LOCAL modes (explain not supported).
     */
    public String resolveExplainScriptPath() {
        if (quantumExecutionMode != ExecutionMode.LOCAL) return null;
        if (!isNullOrBlank(localExplainScriptPath)) return localExplainScriptPath;
        return localScriptPath; // graceful fallback
    }

    // --- Helpers ---

    private static boolean isNullOrBlank(String v) {
        return v == null || v.isBlank();
    }

    private static void require(String name, String value, List<String> errors) {
        if (isNullOrBlank(value)) {
            errors.add(name + " is not set.");
        }
    }

    /** Soft file existence check with WARN level only. */
    private static void softCheckExists(String label, String path) {
        try {
            if (isNullOrBlank(path)) return;
            Path p = Path.of(path);
            if (!Files.exists(p)) {
                // Do not fail: scripts may be copied by a volume or later in CI
                log.warn("[QuantumConfig] {}='{}' does not exist at startup.", label, path);
            }
        } catch (Exception e) {
            log.warn("[QuantumConfig] {} path check failed for '{}': {}", label, path, e.toString());
        }
    }

    /** Mask secrets in logs; keep last 4 chars. */
    private static String mask(String token) {
        if (isNullOrBlank(token)) return "null";
        int n = token.length();
        return (n <= 8) ? "********" : "****" + token.substring(n - 4);
    }

    @Override
    public String toString() {
        return "QuantumConfig{" +
                "pythonExecutable='" + pythonExecutable + '\'' +
                ", quantumIbmToken=" + mask(quantumIbmToken) +
                ", ibmScriptPath='" + ibmScriptPath + '\'' +
                ", localScriptPath='" + localScriptPath + '\'' +
                ", ibmRangeScriptPath='" + ibmRangeScriptPath + '\'' +
                ", localRangeScriptPath='" + localRangeScriptPath + '\'' +
                ", localExplainScriptPath='" + localExplainScriptPath + '\'' +
                ", quantumExecutionMode=" + quantumExecutionMode +
                '}';
    }
}
