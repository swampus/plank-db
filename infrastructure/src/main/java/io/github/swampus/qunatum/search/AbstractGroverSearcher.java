package io.github.swampus.qunatum.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.exception.QuantumInvalidInputException;
import io.github.swampus.model.QuantumResultModel;
import io.github.swampus.ports.QuantumSearcher;
import io.github.swampus.qunatum.QuantumProcessRunner;
import io.github.swampus.dto.QuantumResultDTO;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public abstract class AbstractGroverSearcher implements QuantumSearcher {

    private final QuantumProcessRunner runner;
    private final ObjectMapper objectMapper;
    private final QuantumConfig config;

    public QuantumResultModel search(String key, Set<String> keys) {
        try {
            String keysJson = objectMapper.writeValueAsString(keys);
            Map<String, String> entries = keys.stream()
                    .collect(Collectors.toMap(k -> k, k -> "value_for_" + k));
            String entriesJson = objectMapper.writeValueAsString(entries);

            List<String> args = buildArgs(key, keysJson, entriesJson);
            String scriptPath = getResolvedScriptPath(false);

            System.out.println("\nCONFIG: " + config);
            String result = runner.run(scriptPath, args);
            System.out.println("\nRAW OUTPUT:\n" + result);

            var json = objectMapper.readTree(result);
            System.out.println("\nParsed JSON:\n" + json.toPrettyString());
            return objectMapper.treeToValue(json, QuantumResultModel.class);
        } catch (JsonProcessingException e) {
            throw new QuantumInvalidInputException("Failed to serialize input keys or entries " + e.getMessage(), e);
        }
    }

    protected abstract List<String> buildArgs(String key, String json, String entriesJson);

    protected abstract String getResolvedScriptPath(boolean isRange);

    public QuantumConfig getConfig() {
        return config;
    }
}


