package io.github.swampus.qunatum.search.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.search.AbstractGroverRangeSearcher;

public class GroverLocalRangeSearcher extends AbstractGroverRangeSearcher {

    public GroverLocalRangeSearcher(QuantumConfig quantumConfig, ObjectMapper objectMapper) {
        super(objectMapper, quantumConfig);
    }

    @Override
    protected String[] buildCommand(String fromKey, String toKey, String keysJson) {
        return new String[]{
                getQuantumConfig().getPythonExecutable(),
                getQuantumConfig().getLocalRangeScriptPath(),
                fromKey,
                toKey,
                keysJson
        };
    }
}
