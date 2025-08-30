package io.github.swampus.service;

import io.github.swampus.exception.CollectionNotFoundException;
import io.github.swampus.exception.KeyNotFoundException;
import io.github.swampus.exception.QuantumInvalidRequestException;
import io.github.swampus.exception.QuantumIllegalStateException;
import io.github.swampus.exception.RangeNotFoundException;
import io.github.swampus.model.ExplainPlanModel;
import io.github.swampus.port.out.CollectionReaderPort;
import io.github.swampus.port.out.QuantumDryRunnerPort;
import io.github.swampus.quntum.BackendInfo;
import io.github.swampus.quntum.DryRunResult;
import io.github.swampus.quntum.QueryMode;
import io.github.swampus.quntum.QuantumPlan;
import io.github.swampus.usecase.explain.ExplainPlanInput;
import io.github.swampus.usecase.explain.ExplainQuantumPlanUseCase;
import io.github.swampus.usecase.explain.Strategy;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Use case implementation:
 * Builds an "Explain Quantum Plan" for Grover search (KEY/RANGE) and,
 * for local backend (Aer), performs a dry-run to expose measurement probabilities.
 *
 * Design notes:
 * - Depends on ports (CollectionReaderPort, QuantumDryRunnerPort) instead of other use cases.
 * - No framework annotations; plain Java and explicit constructor for testability.
 * - Returns the plan even if dry-run fails (dryRun=null) to keep endpoint robust.
 *
 * Thread-safety: stateless; collaborators must be thread-safe.
 */
public final class ExplainQuantumPlanService implements ExplainQuantumPlanUseCase {

    private final CollectionReaderPort collectionReader; // read-only access to collection data
    private final QuantumDryRunnerPort dryRunner;        // invokes local Aer python script

    public ExplainQuantumPlanService(CollectionReaderPort collectionReader,
                                     QuantumDryRunnerPort dryRunner) {
        this.collectionReader = requireNonNull(collectionReader, "collectionReader");
        this.dryRunner = requireNonNull(dryRunner, "dryRunner");
    }

    @Override
    public ExplainPlanModel execute(String collection, ExplainPlanInput input) {
        requireNonNull(collection, "collection must not be null");
        requireNonNull(input, "input must not be null");

        // 1) Load all entries of the collection
        Map<String, String> entries = collectionReader.getAllEntries(collection);
        if (entries == null) {
            throw new CollectionNotFoundException(collection);
        }
        if (entries.isEmpty()) {
            throw new QuantumIllegalStateException("Collection is empty: " + collection);
        }

        // 2) Sort keys deterministically and create key->binary encoding
        List<String> keys = new ArrayList<>(entries.keySet());
        keys.sort(Comparator.naturalOrder());

        int n = keys.size();
        int numQubits = computeNumQubits(n);
        Map<String, String> encoding = buildEncodingMap(keys, numQubits);

        // 3) Resolve target (KEY/RANGE) into marked basis states
        QueryMode mode = requireNonNullMode(input.getMode());
        ResolvedTarget target = resolveTarget(
                mode, input.getKey(), input.getFromKey(), input.getToKey(), keys, encoding);

        // 4) Choose iterations via Strategy (AUTO/FIXED)
        int iterations = Strategy
                .defaultIfNull(input.getStrategy())
                .selectIterations(n, target.markedStates().size(), input.getIterations());

        // 5) Coarse metrics and human-readable oracle expression
        int estimatedOracleDepth = estimateOracleDepth(numQubits);
        Map<String, Integer> gateCounts = estimateGateCounts(numQubits, iterations, target.markedStates().size());
        String oracleExpr = buildOracleExpression(target.markedStates());

        // 6) Backend metadata (local vs ibm)
        BackendInfo backend = buildBackendInfo(input.getBackend(), input.getShots(), input.getSeed());

        // 7) Assemble the plan (expose immutable views)
        QuantumPlan plan = new QuantumPlan(
                mode,
                target.label(),
                Collections.unmodifiableMap(encoding),
                List.copyOf(target.markedStates()),
                target.markedStates().size(),
                n,
                numQubits,
                iterations,
                iterations,
                oracleExpr,
                estimatedOracleDepth,
                Collections.unmodifiableMap(gateCounts),
                backend,
                List.of(
                        "Grover ~√(N/M); AUTO uses floor(π/4 * sqrt(N/M)).",
                        "Diffusion amplifies the marked states after the oracle phase flip.",
                        "Depth/gate counts are coarse estimates; actual values depend on oracle synthesis."
                )
        );

        // 8) Dry-run only for local backend; never block explain on simulator errors
        DryRunResult dryRun = isLocalBackend(backend) ? safeDryRun(plan) : null;

        return new ExplainPlanModel(plan, dryRun);
    }

    // ------------------- helpers (small, single-purpose) -------------------

    private QueryMode requireNonNullMode(QueryMode mode) {
        if (mode == null) throw new QuantumInvalidRequestException("mode must be provided (KEY or RANGE)");
        return mode;
    }

    /**
     * Computes the minimal number of qubits to index N items.
     * Uses integer bit-width to avoid floating point edge cases.
     */
    private int computeNumQubits(int n) {
        if (n <= 1) return 1;
        // ceil(log2(n)) using bit operations
        return 32 - Integer.numberOfLeadingZeros(n - 1);
    }

    /**
     * Maps sorted keys to contiguous binary labels [0..N-1] with fixed width = numQubits.
     */
    private Map<String, String> buildEncodingMap(List<String> sortedKeys, int numQubits) {
        Map<String, String> map = new LinkedHashMap<>(sortedKeys.size() * 2);
        for (int i = 0; i < sortedKeys.size(); i++) {
            map.put(sortedKeys.get(i), leftPad(Integer.toBinaryString(i), numQubits));
        }
        return map;
    }

    /**
     * Resolves a user request (KEY/RANGE) into the set of marked states (bitstrings).
     */
    private ResolvedTarget resolveTarget(
            QueryMode mode,
            String key,
            String fromKey,
            String toKey,
            List<String> sortedKeys,
            Map<String, String> encoding
    ) {
        if (mode == QueryMode.KEY) {
            if (key == null || key.isBlank()) {
                throw new QuantumInvalidRequestException("key must be provided for KEY mode");
            }
            String bin = encoding.get(key);
            if (bin == null) throw new KeyNotFoundException(key);
            return new ResolvedTarget(key, List.of(bin));
        }

        // RANGE mode: include all keys in [from..to] (lexicographic)
        String from = (fromKey == null) ? "" : fromKey;
        String to = (toKey == null) ? "\uFFFF" : toKey;

        List<String> inRange = sortedKeys.stream()
                .filter(k -> k.compareTo(from) >= 0 && k.compareTo(to) <= 0)
                .collect(Collectors.toList());

        if (inRange.isEmpty()) throw new RangeNotFoundException("[" + from + ".." + to + "]");

        List<String> marked = inRange.stream().map(encoding::get).toList();
        return new ResolvedTarget("[" + from + ".." + to + "]", marked);
    }

    private int estimateOracleDepth(int numQubits) {
        // Very coarse heuristic; replace with synthesized metrics if/when needed
        return 2 + numQubits;
    }

    private Map<String, Integer> estimateGateCounts(int numQubits, int iterations, int M) {
        // Coarse counts to make the explain response tangible
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("h", numQubits * (iterations + 1));   // initial H^n + per-iteration H in diffuser
        counts.put("x", M * numQubits);                  // X around oracle (depends on marked states)
        counts.put("mcx", M * iterations);               // multi-controlled-X proxies for MCZ
        return counts;
    }

    private String buildOracleExpression(List<String> markedStates) {
        if (markedStates.size() == 1) return "(x" + markedStates.get(0) + ")";
        return markedStates.stream().map(s -> "x" + s).collect(Collectors.joining(" | "));
    }

    private BackendInfo buildBackendInfo(String backendRaw, Integer shots, Integer seed) {
        boolean isIbm = backendRaw != null && backendRaw.equalsIgnoreCase("ibm");
        return new BackendInfo(
                isIbm ? "ibm/runtime" : "local/aer_simulator",
                shots == null ? 2048 : shots,
                seed,
                isIbm
        );
    }

    private boolean isLocalBackend(BackendInfo backend) {
        String name = (backend == null) ? null : backend.name();
        return name != null && name.startsWith("local/");
    }

    private DryRunResult safeDryRun(QuantumPlan plan) {
        try {
            return dryRunner.dryRun(plan);
        } catch (RuntimeException ex) {
            // Keep the plan visible even if the simulator is unavailable or crashes.
            return null;
        }
    }

    private static String leftPad(String s, int width) {
        if (s.length() >= width) return s;
        return "0".repeat(width - s.length()) + s;
    }

    /** Tiny value object to keep intent explicit and method signatures small. */
    private record ResolvedTarget(String label, List<String> markedStates) {}
}
