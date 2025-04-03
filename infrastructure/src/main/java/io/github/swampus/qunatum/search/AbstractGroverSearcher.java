package io.github.swampus.qunatum.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.exception.QuantumInvalidInputException;
import io.github.swampus.ports.QuantumSearcher;
import io.github.swampus.qunatum.QuantumProcessRunner;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public abstract class AbstractGroverSearcher implements QuantumSearcher {

    private final QuantumProcessRunner runner;
    private final ObjectMapper objectMapper;
    private final QuantumConfig config;

    @Override
    public String search(String key, Set<String> keys) {
        try {
            String json = objectMapper.writeValueAsString(keys);
            return runner.run(
                    config.getPythonExecutable(),
                    buildArgs(key, json)
            );
        } catch (JsonProcessingException e) {
            throw new QuantumInvalidInputException("Failed to serialize input keys to JSON", e);
        }
    }

    protected abstract List<String> buildArgs(String key, String json);

    public QuantumConfig getConfig() {
        return config;
    }
}


