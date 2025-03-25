package io.github.swampus.search.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.config.QuantumProperties;
import io.github.swampus.config.QuantumRuntimeConfig;
import io.github.swampus.search.AbstractGroverSearcher;

public class GroverLocalSearcher extends AbstractGroverSearcher {


    public GroverLocalSearcher(QuantumConfig config, ObjectMapper objectMapper) {
        super(config, objectMapper);
    }

    @Override
    protected String[] buildCommand(String key, String keysJson) {
        return new String[] {
                getConfig().getPythonExecutable(),
                getConfig().getLocalScriptPath(),
                key,
                keysJson
        };
    }
}