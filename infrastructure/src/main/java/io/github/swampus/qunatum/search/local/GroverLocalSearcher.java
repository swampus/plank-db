package io.github.swampus.qunatum.search.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.qunatum.search.AbstractGroverSearcher;

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