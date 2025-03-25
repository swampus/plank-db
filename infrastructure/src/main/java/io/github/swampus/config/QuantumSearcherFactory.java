package io.github.swampus.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.ports.QuantumSearcher;
import io.github.swampus.search.ibm.GroverIbmSearcher;
import io.github.swampus.search.local.GroverLocalSearcher;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class QuantumSearcherFactory {

    private final QuantumRuntimeConfig runtimeConfig;
    private final QuantumConfig staticConfig;
    private final ObjectMapper objectMapper;

    public QuantumSearcher create() {
        return switch (runtimeConfig.getMode()) {
            case LOCAL -> new GroverLocalSearcher(staticConfig, objectMapper);
            case IBM, IBM_REAL_PC -> new GroverIbmSearcher(staticConfig, objectMapper);
        };
    }
}
