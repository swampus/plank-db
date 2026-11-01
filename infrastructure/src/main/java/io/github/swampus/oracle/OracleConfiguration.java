package io.github.swampus.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
public class OracleConfiguration {
    @Bean
    public OracleQueryService oracleQueryService(ObjectMapper mapper,
            @Value("${QUANTUM_ORACLE_ENABLED:false}") boolean enabled,
            @Value("${QUANTUM_PYTHON_EXEC:/opt/venv/bin/python}") String python,
            @Value("${QUANTUM_ORACLE_PYTHON_PATH:/app/python}") String pythonPath,
            @Value("${QUANTUM_ORACLE_CACHE_DIR:/app/oracle-cache}") String cachePath,
            @Value("${QUANTUM_ORACLE_MAX_WIDTH:8}") int maxWidth,
            @Value("${QUANTUM_ORACLE_MAX_TOTAL_QUBITS:16}") int maxTotal,
            @Value("${QUANTUM_ORACLE_MAX_SHOTS:8192}") int maxShots,
            @Value("${QUANTUM_ORACLE_MAX_ITERATIONS:32}") int maxIterations,
            @Value("${QUANTUM_ORACLE_TIMEOUT_SECONDS:30}") int timeout,
            @Value("${QUANTUM_ORACLE_CONCURRENCY:2}") int concurrency,
            @Value("${QUANTUM_ORACLE_CACHE_ENTRIES:64}") int cacheEntries) {
        if (cacheEntries < 1 || cacheEntries > 256) throw new IllegalArgumentException("cache entries must be in [1,256]");
        Map<String, String> environment = Map.of("PYTHONPATH", pythonPath,
                "QUANTUM_ORACLE_CACHE_DIR", cachePath, "QUANTUM_ORACLE_MAX_WIDTH", String.valueOf(maxWidth),
                "QUANTUM_ORACLE_MAX_TOTAL_QUBITS", String.valueOf(maxTotal),
                "QUANTUM_ORACLE_MAX_SHOTS", String.valueOf(maxShots),
                "QUANTUM_ORACLE_MAX_ITERATIONS", String.valueOf(maxIterations),
                "QUANTUM_ORACLE_CACHE_ENTRIES", String.valueOf(cacheEntries));
        var runner = new OracleProcessRunner(List.of(python, "-m", "oracle.runner"), environment,
                Duration.ofSeconds(timeout), concurrency, 262144);
        var adapter = new AerOracleAdapter(runner, mapper);
        return new OracleQueryService(adapter, adapter, enabled, maxWidth, maxTotal, maxShots, maxIterations);
    }
}
