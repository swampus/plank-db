package io.github.swampus.quantum.search.ibm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.quantum.QuantumProcessRunner;
import io.github.swampus.quantum.search.AbstractGroverRangeSearcher;

import java.util.List;

public class GroverIbmRangeSearcher extends AbstractGroverRangeSearcher {

    public GroverIbmRangeSearcher(QuantumProcessRunner runner, ObjectMapper objectMapper, QuantumConfig config) {
        super(runner, objectMapper, config);
    }

    @Override
    protected List<String> buildArgs(String fromKey, String toKey, String keysJson) {
        return List.of(
                getConfig().getIbmRangeScriptPath(),
                fromKey,
                toKey,
                keysJson,
                getConfig().getQuantumIbmToken()
        );
    }

    @Override
    protected String getResolvedScriptPath(boolean isRange) {
        return getConfig().getIbmRangeScriptPath();
    }
}
