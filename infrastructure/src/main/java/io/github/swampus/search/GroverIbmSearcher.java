package io.github.swampus.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;

public class GroverIbmSearcher extends AbstractGroverSearcher {

    public GroverIbmSearcher(QuantumConfig config, ObjectMapper objectMapper) {
        super(config, objectMapper);
    }

    @Override
    protected String[] buildCommand(QuantumConfig config, String key, String keysJson) {
        return new String[] {
                config.getPythonExecutable(),
                config.getIbmScriptPath(),
                config.getIbmToken(),
                key,
                keysJson
        };
    }
}
