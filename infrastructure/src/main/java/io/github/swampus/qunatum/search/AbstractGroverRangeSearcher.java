package io.github.swampus.qunatum.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.exception.QuantumInvalidInputException;
import io.github.swampus.model.QuantumResultModel;
import io.github.swampus.ports.QuantumRangeSearcher;
import io.github.swampus.qunatum.QuantumProcessRunner;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Set;

@AllArgsConstructor
public abstract class AbstractGroverRangeSearcher implements QuantumRangeSearcher {

    private final QuantumProcessRunner runner;
    private final ObjectMapper objectMapper;
    private final QuantumConfig config;

    @Override
    public QuantumResultModel searchInRange(Set<String> keys, String fromKey, String toKey) {
        try {
            String jsonKeys = objectMapper.writeValueAsString(keys);
            List<String> args = buildArgs(fromKey, toKey, jsonKeys);

            String scriptPath = getResolvedScriptPath(true);
            String result = runner.run(scriptPath, args);

            JsonNode json = objectMapper.readTree(result);

            return objectMapper.treeToValue(json, QuantumResultModel.class);

        } catch (JsonProcessingException e) {
            throw new QuantumInvalidInputException("Failed to serialize input keys or parse output " + e.getMessage(), e);
        }
    }
    protected abstract List<String> buildArgs(String fromKey, String toKey, String json);

    public QuantumConfig getConfig() {
        return config;
    }

    protected abstract String getResolvedScriptPath(boolean isRange);
}


