package io.github.swampus.oracle;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;

/** Both ports share a fixed local Python protocol. No IBM configuration is consulted. */
public class AerOracleAdapter implements OracleCompilerPort, OracleExecutionPort {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AerOracleAdapter.class);
    private final OracleProcessRunner runner;
    private final ObjectMapper mapper;

    public AerOracleAdapter(OracleProcessRunner runner, ObjectMapper mapper) {
        this.runner = runner;
        this.mapper = mapper.copy().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @Override public OracleReport compile(OracleQuery query) { return invoke(query, OracleProcessRunner.Action.explain); }
    @Override public OracleReport search(OracleQuery query) { return invoke(query, OracleProcessRunner.Action.search); }

    private OracleReport invoke(OracleQuery query, OracleProcessRunner.Action action) {
        try {
            OracleReport result = mapper.readValue(runner.run(action, mapper.writeValueAsString(query)), OracleReport.class);
            if (result == null || result.oracleFingerprint() == null || !result.oracleFingerprint().matches("[a-f0-9]{64}")
                    || result.metrics() == null || result.timings() == null || result.normalizedPredicate() == null
                    || result.measurements() == null || !result.simulated()
                    || !"local/aer_simulator/statevector/CPU".equals(result.backend())
                    || (action == OracleProcessRunner.Action.search && (result.topMeasurement() == null
                    || result.observedValidProbability() == null || result.classicalBaseline() == null)))
                throw new OracleFailure(OracleFailure.Kind.BACKEND, "oracle Python returned an incomplete report");
            LOG.info("Oracle {} backend={} qiskit={} aer={} qubits={} shots={} iterations={} cacheHit={} simulationMs={}",
                    action, result.backend(), result.qiskitVersion(), result.aerVersion(), result.metrics().totalQubits(),
                    result.shots(), result.iterations(), result.cacheHit(), result.timings().simulationMs());
            return result;
        } catch (IOException error) {
            throw new OracleFailure(OracleFailure.Kind.BACKEND, "oracle Python returned malformed JSON");
        }
    }
}
