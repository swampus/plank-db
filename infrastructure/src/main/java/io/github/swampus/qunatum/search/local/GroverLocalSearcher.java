package io.github.swampus.qunatum.search.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.qunatum.QuantumProcessRunner;
import io.github.swampus.qunatum.search.AbstractGroverSearcher;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class GroverLocalSearcher extends AbstractGroverSearcher {

    public GroverLocalSearcher(QuantumProcessRunner runner, ObjectMapper objectMapper, QuantumConfig config) {
        super(runner, objectMapper, config);
    }

    @Override
    protected List<String> buildArgs(String key, String keysJson, String entriesJson) {
        return List.of(
                key,
                keysJson,
                entriesJson,
                "--backend=local"
        );
    }

    @Override
    protected String getResolvedScriptPath(boolean isRange) {
        return getConfig().getLocalScriptPath();
    }
}