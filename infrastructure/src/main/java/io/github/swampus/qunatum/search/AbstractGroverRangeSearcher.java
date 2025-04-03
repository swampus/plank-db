package io.github.swampus.qunatum.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.exception.QuantumInvalidInputException;
import io.github.swampus.ports.QuantumRangeSearcher;
import io.github.swampus.qunatum.QuantumProcessRunner;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@AllArgsConstructor
public abstract class AbstractGroverRangeSearcher implements QuantumRangeSearcher {

    private final QuantumProcessRunner runner;
    private final ObjectMapper objectMapper;
    private final QuantumConfig config;

    @Override
    public Optional<String> searchInRange(Set<String> keys, String fromKey, String toKey) {
        try {
            String json = objectMapper.writeValueAsString(keys);
            String result = runner.run(
                    config.getPythonExecutable(),
                    buildArgs(fromKey, toKey, json)
            );
            return Optional.ofNullable(result);
        } catch (JsonProcessingException e) {
            throw new QuantumInvalidInputException("Failed to serialize input keys to JSON", e);
        }
    }
    protected abstract List<String> buildArgs(String fromKey, String toKey, String json);

    public QuantumConfig getConfig() {
        return config;
    }
}


