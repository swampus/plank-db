package io.github.swampus.qunatum.search.ibm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.search.AbstractGroverRangeSearcher;

public class GroverIbmRangeSearcher extends AbstractGroverRangeSearcher {

    public GroverIbmRangeSearcher(QuantumConfig quantumConfig, ObjectMapper objectMapper) {
        super(objectMapper, quantumConfig);
    }

    @Override
    protected String[] buildCommand(String fromKey, String toKey, String keysJson) {
        return new String[]{
                getQuantumConfig().getPythonExecutable(),
                getQuantumConfig().getIbmRangeScriptPath(),
                getQuantumConfig().getQuantumIbmToken(),
                fromKey,
                toKey,
                keysJson
        };
    }
}
