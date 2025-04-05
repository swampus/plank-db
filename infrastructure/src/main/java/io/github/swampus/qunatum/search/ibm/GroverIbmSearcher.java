package io.github.swampus.qunatum.search.ibm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.qunatum.QuantumProcessRunner;
import io.github.swampus.qunatum.search.AbstractGroverSearcher;

import java.util.List;


public class GroverIbmSearcher extends AbstractGroverSearcher {

    public GroverIbmSearcher(QuantumProcessRunner runner, ObjectMapper objectMapper, QuantumConfig config) {
        super(runner, objectMapper, config);
    }

    @Override
    protected List<String> buildArgs(String key, String keysJson) {
        return List.of(
                getConfig().getIbmScriptPath(),
                key,
                keysJson,
                getConfig().getQuantumIbmToken()
        );
    }
}
