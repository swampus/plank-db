package io.github.swampus.oracle;

/** Independent use cases; no collection reader, marked-state planner or legacy Strategy. */
public class OracleQueryService {
    private final OracleCompilerPort compiler;
    private final OracleExecutionPort executor;
    private final boolean enabled;
    private final int maxWidth, maxTotalQubits, maxShots, maxIterations;

    public OracleQueryService(OracleCompilerPort compiler, OracleExecutionPort executor, boolean enabled,
                              int maxWidth, int maxTotalQubits, int maxShots, int maxIterations) {
        this.compiler = compiler;
        this.executor = executor;
        this.enabled = enabled;
        if (maxWidth < 2 || maxWidth > 8 || maxTotalQubits < 3 || maxTotalQubits > 16
                || maxShots < 1 || maxShots > 8192 || maxIterations < 1 || maxIterations > 32)
            throw new IllegalArgumentException("oracle limits exceed hard resource ceilings");
        this.maxWidth = maxWidth;
        this.maxTotalQubits = maxTotalQubits;
        this.maxShots = maxShots;
        this.maxIterations = maxIterations;
    }

    public OracleReport explain(OracleQuery query) { validate(query); return compiler.compile(query); }
    public OracleReport search(OracleQuery query) { validate(query); return executor.search(query); }

    private void validate(OracleQuery query) {
        if (!enabled) throw new OracleFailure(OracleFailure.Kind.UNAVAILABLE, "Quantum Oracle extension is disabled; set QUANTUM_ORACLE_ENABLED=true");
        if (query == null) throw OracleFailure.invalid("query is required");
        query.validate(maxWidth, maxTotalQubits, maxShots, maxIterations);
    }
}
