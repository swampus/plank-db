package io.github.swampus.qunatum.search.ibm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.search.AbstractGroverSearcher;

public class GroverIbmSearcher extends AbstractGroverSearcher {

    public GroverIbmSearcher(QuantumConfig config, ObjectMapper objectMapper) {
        super(config, objectMapper);
    }

    @Override
    protected String[] buildCommand(String key, String keysJson) {
        return new String[] {
                getConfig().getPythonExecutable(),
                getConfig().getIbmScriptPath(),
                getConfig().getQuantumIbmToken(),
                key,
                keysJson
        };
    }
}
