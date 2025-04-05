package io.github.swampus.qunatum.search.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.qunatum.QuantumProcessRunner;
import io.github.swampus.qunatum.search.AbstractGroverRangeSearcher;

import java.util.List;

public class GroverLocalRangeSearcher extends AbstractGroverRangeSearcher {

    public GroverLocalRangeSearcher(QuantumProcessRunner runner, ObjectMapper objectMapper, QuantumConfig config) {
        super(runner, objectMapper, config);
    }

    @Override
    protected List<String> buildArgs(String fromKey, String toKey, String keysJson) {
        return List.of(
                getConfig().getLocalRangeScriptPath(),
                fromKey,
                toKey,
                keysJson
        );
    }
}
