package io.github.swampus.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.config.QuantumRuntimeConfig;

public class GroverLocalSearcher extends AbstractGroverSearcher {

    public GroverLocalSearcher(QuantumConfig config, ObjectMapper objectMapper) {
        super(config, objectMapper);
    }

    @Override
    protected String[] buildCommand(QuantumConfig config, String key, String keysJson) {
        return new String[] {
                config.getPythonExecutable(),
                config.getLocalScriptPath(),
                key,
                keysJson
        };
    }
}